package com.calorietracker.app.data.ai

import com.calorietracker.app.domain.nutrition.NutritionMath

/**
 * 「AI 给的每 100g 值能不能用」的判定。
 *
 * 单独放一个文件是为了**能单测**——这里是踩过坑的地方：
 *
 * 旧实现用 `?: 0.0` 把「模型没给」当成「就是 0」，再拿这个 0 去做 Atwater 反算
 * （4×0 + 4×0 + 9×0 = 0），与任何热量都不可能自洽 → 整条被判成幻觉丢弃 →
 * 掉到「营养待补充」档 → 结果页这道菜显示 **0 千卡**。
 *
 * 而「不确定就填 null」恰恰是我们自己在 prompt 里要求的（为了拦编造），
 * 所以「只给热量、三个宏量全 null」是**合法且常见**的返回，必须放行。
 *
 * 规则：
 *  1. 热量必须有，且落在 (0, [maxKcalPer100g]]；超上限基本是幻觉（纯油脂约 900）；
 *  2. 任一宏量为负 → 拒绝；
 *  3. **三项宏量齐全**时，必须通过 Atwater 自洽校验（拦「脂肪 1g 却报 900 千卡」这类幻觉）；
 *  4. **缺项**时不拿缺失值去反算（缺失 ≠ 0），只保留第 1 条的区间校验。
 *
 * @param maxKcalPer100g 每 100g 热量上限，由调用方给出（与食物库生成脚本同一口径）
 */
internal fun isAiValueUsable(
    kcal: Double?,
    carbs: Double?,
    protein: Double?,
    fat: Double?,
    maxKcalPer100g: Double = 900.0,
): Boolean {
    if (kcal == null || kcal <= 0.0 || kcal > maxKcalPer100g) return false
    if (listOfNotNull(carbs, protein, fat).any { it < 0.0 }) return false

    val complete = carbs != null && protein != null && fat != null
    if (!complete) return true

    return NutritionMath.isAtwaterConsistent(kcal, carbs!!, protein!!, fat!!)
}

/**
 * 营养值的**字段名容错表**。
 *
 * 名字和份量早就做了多键名容错（`AiJson.optString(json, "name", "foodName", "食物", …)`），
 * 但营养值一直只认死一个键名 —— 模型稍微换个写法（`kcal_per_100g`、`caloriesPer100g`…）
 * 就被当成「没给」，那条食物直接掉到 0 千卡。
 *
 * **只收明确写了「每 100 克」的写法。** 像 `calories` / `energy` / `kcal` / `热量` 这类
 * 不带口径的键名**故意不收**：模型很可能拿它回答「每份」的值，而每份与每 100g 能差好几倍，
 * 静默按每 100g 用会得出严重偏低的数字。这种情况交给第二轮追问（见 AiFoodExtractor）
 * 去要一个带口径的值，而不是在这里猜。
 */
internal val KCAL_PER_100G_KEYS = listOf(
    "kcalPer100g", "kcal_per_100g", "kcalPer100", "kcalPer100G",
    "caloriesPer100g", "calories_per_100g", "energyPer100g", "energy_per_100g",
    "每100克热量", "热量每100克",
)

internal val CARBS_PER_100G_KEYS = listOf(
    "carbsPer100g", "carbs_per_100g", "carbohydratePer100g", "carbohydratesPer100g",
    "carbohydrates_per_100g", "每100克碳水", "每100克碳水化合物",
)

internal val PROTEIN_PER_100G_KEYS = listOf(
    "proteinPer100g", "protein_per_100g", "proteinsPer100g", "每100克蛋白质",
)

internal val FAT_PER_100G_KEYS = listOf(
    "fatPer100g", "fat_per_100g", "fatsPer100g", "每100克脂肪",
)
