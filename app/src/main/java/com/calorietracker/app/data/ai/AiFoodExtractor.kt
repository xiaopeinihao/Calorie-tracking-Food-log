package com.calorietracker.app.data.ai

import android.net.Uri
import com.calorietracker.app.BuildConfig
import com.calorietracker.app.data.local.entity.FoodItem
import com.calorietracker.app.data.repository.FoodRepository
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 食物识别：图片 / 文字 → 可入库的 [DraftItem] 列表。
 *
 * 营养数值的取值顺序（**准确性优先，宁可标「待补充」也不编**）：
 *  1. 名称能在本地权威食物库命中 → 用库里的值（中国食物成分表 / USDA），source 记库名；
 *  2. 命中不了，且 AI 给出的每 100g 值通过了本地校验 → 用 AI 值，source 记「AI 估算」；
 *  3. 都没有 → 克数保留，营养留空并标记「待补充」，由用户在结果页从食物库补上。
 *
 * AI 值同样要过 Atwater 自洽校验（4/4/9），拦住「脂肪 1g 却报 900 千卡」这类幻觉。
 */
@Singleton
class AiFoodExtractor @Inject constructor(
    private val client: AiClient,
    private val configStore: AiConfigStore,
    private val imageEncoder: ImageEncoder,
    private val foodRepository: FoodRepository,
) {

    /**
     * 调试用调用计数。
     *
     * 只打「raw response」看不出同一次操作到底发起了几轮识别（几条日志长得一样），
     * 带编号才能对上。实测一次操作会跑两轮，等待时间凭空翻倍。
     */
    private val callCounter = java.util.concurrent.atomic.AtomicInteger(0)

    /** 拍照识别 */
    suspend fun extractFromImage(uri: Uri): List<DraftItem> {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("AiFoodExtractor", "extractFromImage #${callCounter.incrementAndGet()} $uri")
        }
        val config = configStore.resolve(AiCapability.VISION) ?: throw AiException.notConfigured()
        val jpeg = imageEncoder.toJpeg(uri)
            ?: throw AiException(AiException.Kind.BAD_REQUEST, "读取照片失败")
        val prompt = FoodPrompt.build(
            now = nowText(),
            mealHint = mealHint(),
        )
        val raw = client.chat(config, prompt, AiCapability.VISION, jpeg)
        return parseAndResolve(raw, config)
    }

    /** 一句话记一餐 */
    suspend fun extractFromText(text: String): List<DraftItem> {
        val config = configStore.resolve(AiCapability.TEXT) ?: throw AiException.notConfigured()
        val candidates = candidateNames(text)
        val prompt = FoodPrompt.build(
            now = nowText(),
            mealHint = mealHint(),
            candidates = candidates,
            userText = text,
        )
        val raw = client.chat(config, prompt, AiCapability.TEXT)
        return parseAndResolve(raw, config)
    }

    /** 本地库里跟这段文字沾边的条目名，交给模型复用（提高「AI 名称 ↔ 权威条目」的命中率） */
    private suspend fun candidateNames(text: String): List<String> {
        val tokens = FoodPrompt.keywords(text)
        val names = linkedSetOf<String>()
        tokens.forEach { token ->
            runCatching { foodRepository.searchOnce(token) }
                .getOrDefault(emptyList())
                .take(3)
                .forEach { names += it.name }
        }
        return names.take(12).toList()
    }

    private suspend fun parseAndResolve(raw: String, config: AiProviderConfig): List<DraftItem> {
        // 调试期内把原始返回打到日志：模型不听话时，一眼就能看出它到底回了什么
        if (BuildConfig.DEBUG) {
            android.util.Log.d("AiFoodExtractor", "raw response: " + raw.take(600))
        }
        val array = AiJson.extractArray(raw)
        if (array.isEmpty()) {
            // 必须区分两种「空」：模型**真的回了空数组 `[]`**（照片里没有食物 / 文字里没有食物），
            // 和压根没解析出数组。以前两种情况都报「没解析出 JSON 数组」，
            // 用户明明只是没对准食物，却被提示「可换个模型试试」——纯属误导。
            val excerpt = raw.replace(Regex("\\s+"), " ").take(160)
            val body = raw.trim()
                .removePrefix("```json").removePrefix("```").trim()
                .removeSuffix("```").trim()
            val emptyArray = body.startsWith("[") && body.endsWith("]")
            // 原始返回只进日志：把模型吐的一坨文字贴到界面上，用户既看不懂也无从下手
            if (BuildConfig.DEBUG) android.util.Log.d("AiFoodExtractor", "无法解析的返回：$excerpt")
            throw AiException(
                AiException.Kind.EMPTY_RESPONSE,
                when {
                    raw.isBlank() -> "模型没有返回内容，重试一次"
                    emptyArray -> "模型没识别到食物（返回了空列表），把食物放进取景框再拍一次"
                    else -> "模型这次没按格式返回（已记入日志），再试一次或换个模型"
                },
            )
        }

        val drafts = ArrayList<DraftItem>(array.size)
        // 第一轮没拿到营养值的名字，收集起来交给第二轮追问（见 fillMissingNutrition）
        val missingNutrition = mutableListOf<String>()

        for (index in 0 until array.size) {
            val json: JSONObject = array[index]
            val name = AiJson.optString(json, "name", "foodName", "食物", "食物名") ?: continue
            val grams = (AiJson.optInt(json, "grams")
                ?: AiJson.optInt(json, "estimatedGrams")
                ?: 100).coerceIn(MIN_GRAMS, MAX_GRAMS)
            val confidence = AiJson.optDouble(json, "confidence")?.toFloat()?.coerceIn(0f, 1f)

            val matched = resolve(name)
            val aiPer100 = readAiPer100(json)

            when {
                // 1) 权威库命中：以库为准
                matched != null -> drafts += DraftItem(
                    foodId = matched.id,
                    foodName = matched.name,
                    grams = grams,
                    nutrition = foodRepository.toSummary(matched, grams),
                    userEdited = false,
                    source = matched.source,
                    confidence = confidence,
                )

                // 2) 库里没有，但 AI 给了可用的每 100g 值
                aiPer100 != null -> drafts += DraftItem(
                    foodId = null,
                    foodName = name,
                    grams = grams,
                    nutrition = scale(aiPer100.summary, grams),
                    userEdited = false,
                    source = aiSource(config, aiPer100.complete),
                    confidence = confidence,
                    aiEstimated = true,
                )

                // 3) 都没有：先记账，等下面第二轮追问，不要就这么显示 0 千卡
                else -> {
                    drafts += DraftItem(
                        foodId = null,
                        foodName = name,
                        grams = grams,
                        nutrition = NutritionSummary.EMPTY,
                        userEdited = false,
                        source = "营养待补充",
                        confidence = confidence,
                        needsNutrition = true,
                    )
                    missingNutrition += name
                }
            }
        }
        return fillMissingNutrition(drafts, missingNutrition, config)
    }

    /**
     * 第一轮没拿到营养值的那几项，**再问模型一次**要「每 100 克」的值。
     *
     * 用户的反馈是「AI 不会自己识别自己搜热量吗」—— 第一轮 prompt 为了拦编造写的是
     * 「不确定就填 null，严禁编造」，模型对复合菜/不常见菜经常真的填 null，
     * 于是结果页那几项就成了 0 千卡。与其显示 0，不如明确告诉它「这一轮允许给估算值」，
     * 拿到之后：
     *  1. 仍然过同一道校验（[isAiValueUsable]：区间 + 四项齐全才做 Atwater 自洽）；
     *  2. 出处仍然标成「AI 估算（非成分表）」，与权威值分开显示；
     *  3. 第二轮失败/超时**不影响**第一轮已有结果（[runCatching] 吞掉）。
     */
    private suspend fun fillMissingNutrition(
        drafts: List<DraftItem>,
        missingNames: List<String>,
        config: AiProviderConfig,
    ): List<DraftItem> {
        if (missingNames.isEmpty()) return drafts
        // 最多追问 5 个：每多一次追问就是多一次等待成本，剩下的留给用户从食物库补
        val names = missingNames.distinct().take(MAX_LOOKUP_NAMES)
        val filled = lookupPer100(names, config)
        if (filled.isEmpty()) return drafts
        // 出处要标**真正给出这些值**的那个模型（第二轮可能用的是文本模型，不是视觉模型）
        val capability = lookupCapability(config)
        return drafts.map { draft ->
            if (!draft.needsNutrition) return@map draft
            val per = filled[draft.foodName] ?: return@map draft
            draft.copy(
                nutrition = scale(per.summary, draft.grams),
                source = aiSource(config, per.complete, config.modelFor(capability)),
                aiEstimated = true,
                needsNutrition = false,
            )
        }
    }

    /** 第二轮只问营养值。优先用文本模型（更便宜更快）；没配文本模型就沿用同一个视觉模型。 */
    private suspend fun lookupPer100(
        names: List<String>,
        config: AiProviderConfig,
    ): Map<String, AiPer100> {
        val capability = lookupCapability(config)
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "AiFoodExtractor",
                "lookup 开始 capability=$capability model=${config.modelFor(capability)} names=$names",
            )
        }
        // 第二轮是「锦上添花」，绝不能让总等待被它拉爆：
        // 绑定的文本模型若是**深度思考模型**（本机就是 hy4-preview），实测 60 秒都没返回，
        // 而 AiClient 的整通上限是 75 秒 —— 那样用户要「先等 12 秒识别，再干等 75 秒补值」。
        // 所以这里给它一个**独立的短预算**：超时就放弃，该项仍标「营养待补充」，
        // 用户可以直接从食物库补上（结果页本来就有那个入口）。
        //
        // 说明：OkHttp 的阻塞调用不会因为协程取消而立刻中断，超时后那个请求会在后台
        // 一直跑到它自己的 socket 超时为止 —— 结果会被丢弃，只是暂时占一个 IO 线程。
        val raw = try {
            kotlinx.coroutines.withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                client.chat(config, FoodPrompt.buildNutritionLookup(names), capability)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 第二轮失败绝不能连累第一轮的结果，但**必须留下痕迹**
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "AiFoodExtractor",
                    "lookup 失败：${e.javaClass.simpleName} ${e.message}",
                )
            }
            null
        }
        if (raw == null) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "AiFoodExtractor",
                    "lookup 超时或失败，放弃补值（该项保持「营养待补充」，可从食物库补）",
                )
            }
            return emptyMap()
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.d("AiFoodExtractor", "lookup response: " + raw.take(400))
        }
        val array = AiJson.extractArray(raw)
        val out = LinkedHashMap<String, AiPer100>()
        for (index in 0 until array.size) {
            val json = array[index]
            val name = AiJson.optString(json, "name", "foodName", "食物", "食物名") ?: continue
            val per = readAiPer100(json) ?: continue
            out[name] = per
        }
        return out
    }

    /** 名称落到本地库：先精确/别名，再剥掉括号重试一次 */
    private suspend fun resolve(name: String): FoodItem? {
        foodRepository.resolveByName(name)?.let { return it }
        val normalized = normalize(name)
        if (normalized != name) {
            foodRepository.resolveByName(normalized)?.let { return it }
        }
        return null
    }

    private fun normalize(name: String): String =
        name.replace(Regex("[（(\\[［].*?[)）\\]］]"), "")
            .replace(" ", "")
            .trim()

    /** AI 给的每 100g 值；[complete] 为 false 表示只给了热量、三个宏量没给 */
    private data class AiPer100(val summary: NutritionSummary, val complete: Boolean)

    /**
     * AI 估算的出处文案。缺宏量时必须写清楚，别让人以为碳水/蛋白/脂肪真的是 0。
     *
     * [model] 必须是**真正给出这个值的模型**：默认取视觉模型（第一轮拍照识别走的就是它）；
     * 第二轮追问可能用的是文本模型，那时要显式传进来，否则会把文本模型给的值记到
     * 视觉模型名下，出处就成了错的。
     */
    private fun aiSource(
        config: AiProviderConfig,
        complete: Boolean,
        model: String = config.modelFor(AiCapability.VISION).ifBlank { config.textModel },
    ): String = if (complete) {
        "AI 估算 · $model"
    } else {
        "AI 估算 · 仅热量（碳水/蛋白/脂肪未给）· $model"
    }

    /** 第二轮追问用哪个模型：优先文本模型（更便宜更快），没配文本模型就沿用视觉模型 */
    private fun lookupCapability(config: AiProviderConfig): AiCapability =
        if (config.textModel.isNotBlank()) AiCapability.TEXT else AiCapability.VISION

    /**
     * 读 AI 给的每 100g 值。
     *
     * **关键：区分「模型没给」和「模型给了 0」。**
     *
     * prompt 里明确要求「不确定就填 null，严禁编造」，所以模型对一道复合菜很常见的行为是
     * 只给一个 kcalPer100g、三个宏量全填 null。旧实现用 `?: 0.0` 把 null 当成 0，
     * 再拿这个 0 去做 Atwater 反算（4×0+4×0+9×0=0），与任何热量都不可能自洽 →
     * 整条被判成幻觉丢弃 → 掉到「营养待补充」档 → 结果页显示 **0 千卡**。
     *
     * 现在分两档：
     *  - **四项齐全** → Atwater 自洽校验照旧，一点不放宽（拦「脂肪 1g 却报 900 千卡」这类幻觉）；
     *  - **缺项** → 不拿缺失值去反算（缺失 ≠ 0），只保留绝对区间校验（0 < kcal ≤ 900），
     *    并按「仅热量」标注出处。宏量缺失时按 0 参与合计，但出处文案写明了「未给」，
     *    不会让人误以为这道菜真的没有碳水和脂肪。
     */
    private fun readAiPer100(json: JSONObject): AiPer100? {
        // 多键名容错：模型写 kcal_per_100g / caloriesPer100g 也要认（键名表在 AiValueRule.kt）
        val kcal = AiJson.optDoubleAny(json, KCAL_PER_100G_KEYS)
        val carbs = AiJson.optDoubleAny(json, CARBS_PER_100G_KEYS)
        val protein = AiJson.optDoubleAny(json, PROTEIN_PER_100G_KEYS)
        val fat = AiJson.optDoubleAny(json, FAT_PER_100G_KEYS)
        // 「能不能用」的规则抽到了 AiValueRule.kt（纯函数 + 单测钉住），
        // 免得哪天又退化成「把 null 当 0 去做 Atwater 反算」。
        if (!isAiValueUsable(kcal, carbs, protein, fat, MAX_KCAL_PER_100G)) return null
        return AiPer100(
            summary = NutritionSummary(
                kcal = kcal ?: 0.0,
                carbs = carbs ?: 0.0,
                protein = protein ?: 0.0,
                fat = fat ?: 0.0,
            ),
            complete = carbs != null && protein != null && fat != null,
        )
    }

    private fun scale(per100: NutritionSummary, grams: Int) = NutritionSummary(
        kcal = NutritionMath.scalePer100g(per100.kcal, grams),
        carbs = NutritionMath.scalePer100g(per100.carbs, grams),
        protein = NutritionMath.scalePer100g(per100.protein, grams),
        fat = NutritionMath.scalePer100g(per100.fat, grams),
    )

    private fun nowText(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun mealHint(): String = when (java.time.LocalTime.now().hour) {
        in 5..10 -> "早餐"
        in 11..14 -> "午餐"
        in 17..21 -> "晚餐"
        else -> "加餐"
    }

    private companion object {
        const val MIN_GRAMS = 5
        const val MAX_GRAMS = 2000
        /** 纯油脂约 900 kcal/100g，超过必是幻觉 */
        const val MAX_KCAL_PER_100G = 900.0
        /** 第二轮最多追问几个食物的营养值（每多一个就多一次等待成本） */
        const val MAX_LOOKUP_NAMES = 5

        /**
         * 第二轮追问的**独立超时**。
         *
         * 必须远小于 AiClient 的整通上限（75 秒）：第二轮只是补值，不能让它把
         * 「按完快门到出结果」的时间翻几倍。15 秒内给不出就放弃，交回给用户从食物库补。
         */
        const val LOOKUP_TIMEOUT_MS = 15_000L
    }
}

/** 供外部（比如手动录入页的「问 AI」）复用的日期键 */
internal fun todayKeyForPrompt(): String = DateUtil.todayKey()
