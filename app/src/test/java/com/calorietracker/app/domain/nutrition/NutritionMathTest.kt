package com.calorietracker.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 热量换算的单测。这些断言直接对着权威数据库的真实数值写，
 * 一旦有人改了换算口径，这里会立刻红。
 */
class NutritionMathTest {

    @Test
    fun `scalePer100g 按克数线性缩放`() {
        // 米饭（蒸，代表值）：116 kcal / 25.9g 碳水 / 2.6g 蛋白 / 0.3g 脂肪（每 100g）
        assertEquals(232.0, NutritionMath.scalePer100g(116.0, 200), 0.001)
        assertEquals(58.0, NutritionMath.scalePer100g(116.0, 50), 0.001)
        assertEquals(25.9, NutritionMath.scalePer100g(25.9, 100), 0.001)
        assertEquals(0.0, NutritionMath.scalePer100g(116.0, 0), 0.001)
    }

    @Test
    fun `米饭 200g 的热量等于 232 千卡`() {
        val summary = NutritionSummary(
            kcal = NutritionMath.scalePer100g(116.0, 200),
            carbs = NutritionMath.scalePer100g(25.9, 200),
            protein = NutritionMath.scalePer100g(2.6, 200),
            fat = NutritionMath.scalePer100g(0.3, 200),
        )
        assertEquals(232.0, summary.kcal, 0.001)
        assertEquals(51.8, summary.carbs, 0.001)
        assertEquals(5.2, summary.protein, 0.001)
        assertEquals(0.6, summary.fat, 0.001)
    }

    @Test
    fun `Atwater 反算能自查权威数据是否自洽`() {
        // 米饭：4×25.9 + 4×2.6 + 9×0.3 = 116.7，与自报 116 只差 0.6%
        assertTrue(NutritionMath.isAtwaterConsistent(116.0, 25.9, 2.6, 0.3))
        // 花生油：9×99.9 = 899.1 vs 899
        assertTrue(NutritionMath.isAtwaterConsistent(899.0, 0.0, 0.0, 99.9))
        // 一眼错的抄录值必须被拦住
        assertFalse(NutritionMath.isAtwaterConsistent(116.0, 90.0, 2.6, 0.3))
    }

    @Test
    fun `完成率封顶在 1 且不会除零`() {
        assertEquals(0.5f, NutritionMath.completionRate(800.0, 1600.0), 0.0001f)
        assertEquals(1f, NutritionMath.completionRate(3200.0, 1600.0), 0.0001f)
        assertEquals(0f, NutritionMath.completionRate(800.0, 0.0), 0.0001f)
    }

    @Test
    fun `百分比照实显示超额`() {
        assertEquals(78, NutritionMath.completionPercent(1240.0, 1600.0))
        assertEquals(150, NutritionMath.completionPercent(2400.0, 1600.0))
        assertEquals(0, NutritionMath.completionPercent(100.0, 0.0))
    }

    @Test
    fun `环比在没有上期数据时返回 null 而不是负一百`() {
        assertNull(NutritionMath.periodOverPeriodChange(1520.0, 0.0))
        assertEquals(-0.08, NutritionMath.periodOverPeriodChange(1520.0, 1652.0)!!, 0.001)
    }

    @Test
    fun `超标部分不会算成负数`() {
        assertEquals(0.0, NutritionMath.overTarget(1200.0, 1600.0), 0.001)
        assertEquals(400.0, NutritionMath.overTarget(2000.0, 1600.0), 0.001)
    }

    @Test
    fun `汇总按天累加`() {
        val total = NutritionSummary.of(
            listOf(
                NutritionSummary(232.0, 51.8, 5.2, 0.6),
                NutritionSummary(174.0, 5.2, 8.6, 11.0),
                NutritionSummary(96.0, 4.6, 2.6, 8.2),
            )
        )
        assertEquals(502.0, total.kcal, 0.001)
        assertEquals(61.6, total.carbs, 0.001)
        assertEquals(16.4, total.protein, 0.001)
        assertEquals(19.8, total.fat, 0.001)
    }
}
