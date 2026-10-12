package com.calorietracker.app.data.local.seed

import android.content.Context
import androidx.core.content.edit
import com.calorietracker.app.data.local.dao.FoodDao
import com.calorietracker.app.data.local.entity.FoodItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把随包携带的**权威食物库**写进 Room。
 *
 * 数据文件 `assets/foods_seed.json` 不是手编的，而是由 `tools/build-food-db.py`
 * 从两个权威来源生成，每一行都带 `source` / `sourceRef`，能逐条回溯：
 *  1. 《中国食物成分表 标准版（第 6 版）》—— 中文食物主来源；
 *  2. USDA FoodData Central（SR Legacy，公有领域）—— 覆盖西式/进口食材，附常用中文别名。
 *
 * 关于版本：食物库内容会随版本变化，而**已经装过 App 的用户不能因为
 * 「库非空」就永远拿不到新食物**。所以这里按内容哈希比对，版本变了就重灌一遍。
 *
 * 注意是**重灌**而不是增量 upsert：食物库不但会「加条目」，也会「删条目」。
 * 实测踩过这个坑 —— 早期版本把 USDA 那 7,646 条英文条目也导了进来，
 * 后来改成只保留人工中文命名的那部分，结果老用户的设备上英文条目一直在，
 * 搜索和「常见食物」里照样看得见。只 upsert 永远删不掉它们。
 *
 * 删除范围严格限定为 `ai_estimated = 0` 的种子条目：
 *   * 用户自己存的「我的食物」用 `ai:` 前缀 id 且 ai_estimated = 1，不受影响；
 *   * 历史记录在 `entry_items` 里存的是当时的快照（名称 + 数值），也不受影响。
 */
@Singleton
class FoodSeedLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodDao: FoodDao,
) {

    private val prefs = context.getSharedPreferences("food_seed", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded(): Int = withContext(Dispatchers.IO) {
        val version = readVersion() ?: return@withContext 0
        val installed = prefs.getString(KEY_VERSION, null)
        // 比的是「上次**真正写进去**的条目数」，不是版本号前段。
        //
        // 以前拿 `version.substringBefore('-')` 当期望值，那是生成器写的**原始数组长度**；
        // 而 parseAsset 会跳过营养值缺失、名字为空的条目。只要有一条被跳过，
        // 「库里行数 ≠ 版本号前段」就永远成立 → **每次启动都全量删除重灌 1840 行**，
        // 而且完全没有日志能看出原因。记真实写入数既能正常跳过，也保留了
        // 「库里多出历史残留条目时自愈」的能力（COUNT 对不上就重灌）。
        val installedCount = prefs.getInt(KEY_COUNT, -1)
        if (version == installed && installedCount > 0 && foodDao.countSeedItems() == installedCount) {
            return@withContext 0
        }

        // 解析失败绝不能把异常抛出去：这段跑在 Application 的无主协程里，
        // 逃逸会直接崩进程 —— 种子文件一旦损坏，用户就「每开一次崩一次」。
        // 失败就安静地沿用已有库存（搜索照常可用）。
        val items = runCatching { parseAsset() }
            .onFailure { android.util.Log.e(TAG, "foods_seed.json 解析失败，保留现有库存", it) }
            .getOrDefault(emptyList())
        if (items.isEmpty()) return@withContext 0
        // 删除 + 写入放在**同一个事务**里（见 FoodDao.replaceSeedItems）：
        // 拆成两次的话中间有几十毫秒的「空库窗口」，用户刚好这时搜索/离线识别就会看到「没有食物」。
        foodDao.replaceSeedItems(items, CHUNK)
        prefs.edit {
            putString(KEY_VERSION, version)
            putInt(KEY_COUNT, items.size)
        }
        items.size
    }

    /** 版本号放在单独的小文件里：启动时不必为了比版本去解析 2MB 的 JSON */
    private fun readVersion(): String? = runCatching {
        context.assets.open(VERSION_ASSET).bufferedReader().use { it.readText() }.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun parseAsset(): List<FoodItem> {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("foods")
        val result = ArrayList<FoodItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val kcal = o.optDouble("kcal", -1.0)
            val carbs = o.optDouble("carbs", -1.0)
            val protein = o.optDouble("protein", -1.0)
            val fat = o.optDouble("fat", -1.0)
            // 任何一项营养值缺失或为负的条目一律不入库：宁可少一条，也不给假数字
            if (kcal < 0 || carbs < 0 || protein < 0 || fat < 0) continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            result += FoodItem(
                id = o.getString("id"),
                name = name,
                category = o.optString("category", "其他"),
                kcalPer100g = kcal,
                carbsPer100g = carbs,
                proteinPer100g = protein,
                fatPer100g = fat,
                commonServing = o.optString("serving", "一份约 100g"),
                commonServingGrams = o.optInt("servingGrams", 100),
                source = o.optString("source", "未知"),
                sourceRef = o.optString("sourceRef").takeIf { it.isNotEmpty() },
                alias = o.optString("alias").takeIf { it.isNotEmpty() },
                cooked = o.optBoolean("cooked", false),
            )
        }
        return result.sortedBy { it.name }
    }

    companion object {
        const val ASSET_NAME = "foods_seed.json"
        const val VERSION_ASSET = "foods_seed.version"

        /** 上次写入的种子版本号 */
        private const val KEY_VERSION = "seed_version"

        /** 上次**真正写进库**的条目数（不是版本号里的原始条数） */
        private const val KEY_COUNT = "seed_count"

        private const val CHUNK = 400

        private const val TAG = "FoodSeedLoader"
    }
}
