package com.calorietracker.app.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「AI 每 100g 值能不能用」的回归保护。
 *
 * 背景：曾经用 `?: 0.0` 把「模型没给」当成 0，再拿 0 去做 Atwater 反算，
 * 于是「只给热量、宏量填 null」这种——恰恰是我们在 prompt 里要求的——合法返回
 * 被整条丢弃，结果页显示 0 千卡。
 *
 * 下面第 2、3 条就是钉住这个行为的：缺项时必须放行热量，不能拿缺失值去反算。
 */
class AiValueRuleTest {

    @Test
    fun `四项齐全且自洽时可用`() {
        // 番茄炒蛋的真实返回：99 kcal / 4.8C / 5.3P / 6.8F（Atwater 反算 101.6，偏差 2.6%）
        assertTrue(isAiValueUsable(99.0, 4.8, 5.3, 6.8))
    }

    @Test
    fun `只给热量、三个宏量都没给时可用（缺失不等于 0）`() {
        assertTrue(isAiValueUsable(180.0, null, null, null))
    }

    @Test
    fun `只给热量和部分宏量时可用`() {
        assertTrue(isAiValueUsable(180.0, null, 8.0, 9.0))
    }

    @Test
    fun `四项齐全但不自洽时拒绝（拦幻觉）`() {
        // 脂肪 1g 却报 900 千卡
        assertFalse(isAiValueUsable(900.0, 0.0, 1.0, 1.0))
    }

    @Test
    fun `缺热量时拒绝`() {
        assertFalse(isAiValueUsable(null, 10.0, 5.0, 3.0))
    }

    @Test
    fun `热量为零或为负时拒绝`() {
        assertFalse(isAiValueUsable(0.0, null, null, null))
        assertFalse(isAiValueUsable(-5.0, null, null, null))
    }

    @Test
    fun `热量超过纯油脂上限时拒绝`() {
        assertFalse(isAiValueUsable(1200.0, null, null, null))
    }

    @Test
    fun `任一宏量为负时拒绝`() {
        assertFalse(isAiValueUsable(180.0, -1.0, 8.0, 9.0))
    }

    @Test
    fun `热量字段名容错覆盖常见的每100克写法`() {
        // 模型换个键名（下划线/驼峰）时不能被当成「没给」，否则那条食物会掉到 0 千卡
        assertTrue("kcalPer100g" in KCAL_PER_100G_KEYS)
        assertTrue("kcal_per_100g" in KCAL_PER_100G_KEYS)
        assertTrue("caloriesPer100g" in KCAL_PER_100G_KEYS)
        assertTrue("energyPer100g" in KCAL_PER_100G_KEYS)
    }

    @Test
    fun `宏量字段名容错覆盖常见写法`() {
        assertTrue("carbsPer100g" in CARBS_PER_100G_KEYS)
        assertTrue("carbohydratesPer100g" in CARBS_PER_100G_KEYS)
        assertTrue("proteinPer100g" in PROTEIN_PER_100G_KEYS)
        assertTrue("fatPer100g" in FAT_PER_100G_KEYS)
    }

    @Test
    fun `不带每100克口径的裸键名故意不收`() {
        // 「每份」和「每 100g」能差好几倍，宁可交给第二轮追问去要带口径的值，也不在这里猜
        assertFalse("calories" in KCAL_PER_100G_KEYS)
        assertFalse("energy" in KCAL_PER_100G_KEYS)
        assertFalse("kcal" in KCAL_PER_100G_KEYS)
    }
}
