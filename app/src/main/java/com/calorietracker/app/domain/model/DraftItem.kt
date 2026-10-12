package com.calorietracker.app.domain.model

import com.calorietracker.app.domain.nutrition.NutritionSummary

/**
 * 「还没落库」的一条食物，是识别结果页 / 手动录入页 / 编辑已有记录时的统一草稿模型。
 *
 * 注意：热量与营养素是**由权威食物库按克数算出来的快照**，
 * 一旦确认记录就原样写进 Room，日后食物库数值变化不会改写历史。
 */
data class DraftItem(
    /** 关联的食物库 id；纯手写且库里查不到时为 null */
    val foodId: String? = null,
    val foodName: String,
    val grams: Int,
    val nutrition: NutritionSummary,
    /** 用户是否手动调整过份量或手动添加过（结果页要打标记） */
    val userEdited: Boolean = false,
    /** 数据出处，用于在结果页展示「来自中国食物成分表第 6 版」这类可核对信息 */
    val source: String? = null,
    /** 识别置信度，仅拍照来源有值 */
    val confidence: Float? = null,
    /**
     * 营养值来自 AI 估算（本地食物库查不到该食物）。
     * 界面上必须明确标出来，用户才知道这个数字的分量与「成分表」不同。
     */
    val aiEstimated: Boolean = false,
    /** 连 AI 都给不出自洽的营养值：克数保留，营养留空，等用户从食物库补 */
    val needsNutrition: Boolean = false,
) {
    val kcal: Double get() = nutrition.kcal
    val carbs: Double get() = nutrition.carbs
    val protein: Double get() = nutrition.protein
    val fat: Double get() = nutrition.fat
}
