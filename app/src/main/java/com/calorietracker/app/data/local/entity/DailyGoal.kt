package com.calorietracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日目标。以日期为主键；[DEFAULT_DATE] 这一行作为「全局默认目标」，
 * 当某天没有单独设定时回落到它。
 */
@Entity(tableName = "daily_goals")
data class DailyGoal(
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "target_kcal") val targetKcal: Int,
    @ColumnInfo(name = "target_carbs") val targetCarbs: Int,
    @ColumnInfo(name = "target_protein") val targetProtein: Int,
    @ColumnInfo(name = "target_fat") val targetFat: Int,
) {
    companion object {
        const val DEFAULT_DATE = "__default__"

        /** 产品默认目标：1600 kcal，宏量按 50%/20%/30% 供能拆分 */
        fun default(): DailyGoal = DailyGoal(
            date = DEFAULT_DATE,
            targetKcal = 1600,
            targetCarbs = 200,  // 1600*0.5/4
            targetProtein = 80, // 1600*0.2/4
            targetFat = 53,     // 1600*0.3/9
        )
    }
}
