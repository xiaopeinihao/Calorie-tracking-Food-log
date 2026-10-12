package com.calorietracker.app.domain.nutrition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 热量与营养换算的唯一入口。所有界面显示的数字都必须经过这里，禁止在 UI 层另写算式。
 *
 * 口径说明（很重要，决定了数字「对不对」）：
 * - 食物库里的热量一律是「每 100g 可食部」的千卡值，直接来自权威数据库，不做二次拟合。
 * - 份量换算只做线性缩放：`每100g值 × 克数 / 100`。
 * - [kcalFromMacros] 用 Atwater 系数 4/4/9 反算，只用于**数据质检**（核对数据库自报热量
 *   与三大营养素是否自洽），不用于覆盖数据库给出的热量。
 */
object NutritionMath {

    /** 蛋白质与碳水：4 kcal/g */
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_PROTEIN = 4.0
    /** 脂肪：9 kcal/g */
    const val KCAL_PER_G_FAT = 9.0

    /** 质检用：数据库自报热量与 Atwater 反算值的相对偏差容忍上限（超过则视为可疑数据） */
    const val ATWATER_TOLERANCE = 0.25

    /** 按克数把「每 100g」的值线性缩放到本次份量 */
    fun scalePer100g(per100g: Double, grams: Int): Double = per100g * grams / 100.0

    /** Atwater 反算热量（仅用于质检） */
    fun kcalFromMacros(carbs: Double, protein: Double, fat: Double): Double =
        carbs * KCAL_PER_G_CARBS + protein * KCAL_PER_G_PROTEIN + fat * KCAL_PER_G_FAT

    /** 数据库自报热量与 Atwater 反算值是否自洽 */
    fun isAtwaterConsistent(kcal: Double, carbs: Double, protein: Double, fat: Double): Boolean {
        val computed = kcalFromMacros(carbs, protein, fat)
        if (kcal <= 0.0) return false
        return abs(computed - kcal) / kcal <= ATWATER_TOLERANCE
    }

    /**
     * 目标完成率（0f..1f，超过目标也不超过 1f；用于圆环与进度条）。
     * 目标 <= 0 时返回 0f，避免除零。
     */
    fun completionRate(intake: Double, target: Double): Float {
        if (target <= 0.0) return 0f
        return (intake / target).coerceIn(0.0, 1.0).toFloat()
    }

    /** 真实百分比（可能 > 1），用于文案，如「已摄入 124%」 */
    fun completionPercent(intake: Double, target: Double): Int {
        if (target <= 0.0) return 0
        return ((intake / target) * 100).roundToInt()
    }

    /**
     * 环比涨跌。previous <= 0 时返回 null（无法计算环比，UI 显示「—」而不是 -100%）。
     */
    fun periodOverPeriodChange(current: Double, previous: Double): Double? {
        if (previous <= 0.0) return null
        return (current - previous) / previous
    }

    /** 摄入超出目标的部分（未超出返回 0） */
    fun overTarget(intake: Double, target: Double): Double = max(0.0, intake - target)

    /** 千卡统一显示为整数 */
    fun formatKcal(kcal: Double): String = kcal.roundToInt().toString()

    /** 克数保留一位小数（0 位小数会看不出 0.4g 的差别） */
    fun formatGram(gram: Double): String {
        val rounded = (gram * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}

/**
 * 某一天的营养汇总。所有字段都来自当天的 [com.calorietracker.app.data.local.entity.EntryItem] 快照求和。
 */
data class NutritionSummary(
    val kcal: Double = 0.0,
    val carbs: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(other: NutritionSummary) = NutritionSummary(
        kcal = kcal + other.kcal,
        carbs = carbs + other.carbs,
        protein = protein + other.protein,
        fat = fat + other.fat,
    )

    companion object {
        fun of(items: Iterable<NutritionSummary>): NutritionSummary =
            items.fold(NutritionSummary()) { acc, it -> acc + it }

        val EMPTY = NutritionSummary()
    }
}
