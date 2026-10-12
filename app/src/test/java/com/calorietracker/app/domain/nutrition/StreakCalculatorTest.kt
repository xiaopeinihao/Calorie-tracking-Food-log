package com.calorietracker.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 9, 30)

    private fun key(daysAgo: Long): String = today.minusDays(daysAgo).toString()

    @Test
    fun `没有任何记录时为 0`() {
        assertEquals(0, StreakCalculator.streakOf(emptyList(), today))
    }

    @Test
    fun `连续记录含今天`() {
        val dates = listOf(key(0), key(1), key(2))
        assertEquals(3, StreakCalculator.streakOf(dates, today))
    }

    @Test
    fun `今天还没记不清零 从昨天起算`() {
        val dates = listOf(key(1), key(2), key(3))
        assertEquals(3, StreakCalculator.streakOf(dates, today))
    }

    @Test
    fun `昨天也没记则归零`() {
        val dates = listOf(key(2), key(3))
        assertEquals(0, StreakCalculator.streakOf(dates, today))
    }

    @Test
    fun `中间断一天只算到断点`() {
        val dates = listOf(key(0), key(1), key(3), key(4))
        assertEquals(2, StreakCalculator.streakOf(dates, today))
    }

    @Test
    fun `重复日期不会重复计数`() {
        val dates = listOf(key(0), key(0), key(1))
        assertEquals(2, StreakCalculator.streakOf(dates, today))
    }
}
