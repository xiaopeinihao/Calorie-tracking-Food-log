package com.calorietracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地食物库。所有营养值均为「每 100g」标准值。
 *
 * 数据来源：生鲜食材（米、菜、肉、蛋、奶、水果等）采用《中国食物成分表（第 6 版）》
 * 的每 100g 可食部标准值；常见家常菜（含油盐烹饪）采用行业常用估算值。
 * 详见 [com.calorietracker.app.data.local.seed.FoodSeed] 的注释。
 */
@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    /** 分类，如 主食 / 蔬菜 / 肉蛋 / 家常菜 ... 用于手动录入页的快捷筛选 */
    @ColumnInfo(name = "category") val category: String,
    /** 每 100g 热量（千卡） */
    @ColumnInfo(name = "kcal_per_100g") val kcalPer100g: Double,
    /** 每 100g 碳水（克） */
    @ColumnInfo(name = "carbs_per_100g") val carbsPer100g: Double,
    /** 每 100g 蛋白质（克） */
    @ColumnInfo(name = "protein_per_100g") val proteinPer100g: Double,
    /** 每 100g 脂肪（克） */
    @ColumnInfo(name = "fat_per_100g") val fatPer100g: Double,
    /** 常见分量描述，如「一碗约 200g」「一个约 50g」 */
    @ColumnInfo(name = "common_serving") val commonServing: String,
    /** 该常见分量对应的克数，用于识别/快捷录入时给默认值 */
    @ColumnInfo(name = "common_serving_grams") val commonServingGrams: Int,
    /** 数据出处（权威数据库名），UI 的「我的 → 关于」里会展示，不允许留空 */
    @ColumnInfo(name = "source") val source: String,
    /** 出处里的原始条目号，便于逐条回溯核对 */
    @ColumnInfo(name = "source_ref") val sourceRef: String? = null,
    /**
     * 搜索别名（逗号分隔），例如「米饭（蒸，代表值）」的别名为「白米饭,米饭」，
     * 让用户用日常叫法也能搜到权威条目。别名只影响检索，不影响任何营养数值。
     */
    @ColumnInfo(name = "alias") val alias: String? = null,
    /** 是否为烹饪后（熟）状态。米饭/面条这类「生熟差 3 倍」的食物必须标清楚 */
    @ColumnInfo(name = "cooked") val cooked: Boolean = false,
    /**
     * 是否由 AI 估算并保存进库。这类条目的数值档次与权威成分表不同，
     * 界面（搜索列表、结果页、记录详情）都要区分显示，不能混着看。
     */
    @ColumnInfo(name = "ai_estimated", defaultValue = "0") val aiEstimated: Boolean = false,
)
