package com.calorietracker.app.domain.nutrition

import java.time.LocalDate

/**
 * 连续记录天数。产品上「连续打卡」比任何图表都更能留住人，所以口径必须写死且有单测：
 *
 * - 从**今天**往前数；
 * - 今天还没记（比如早上十点）**不清零**，从昨天起算；
 * - 昨天也没记，则为 0。
 */
object StreakCalculator {

    /**
     * @param distinctDatesDesc 有记录的日期（yyyy-MM-dd），倒序，允许重复
     * @param today 今天的日期，便于测试注入
     */
    fun streakOf(distinctDatesDesc: List<String>, today: LocalDate): Int {
        if (distinctDatesDesc.isEmpty()) return 0
        val set = distinctDatesDesc.toHashSet()

        var cursor = today
        if (!set.contains(cursor.toString())) {
            cursor = cursor.minusDays(1)
            if (!set.contains(cursor.toString())) return 0
        }

        var streak = 0
        while (set.contains(cursor.toString())) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
