package com.calorietracker.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 日期工具。全部使用本地时区的 yyyy-MM-dd 字符串作为「一天」的键，
 * 与 Room 里 `meal_entries.date` 的存储格式保持一致（minSdk 29 可直接用 java.time）。
 */
object DateUtil {

    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTH_DAY = DateTimeFormatter.ofPattern("M月d日")

    val today: LocalDate get() = LocalDate.now()

    fun todayKey(): String = today.format(ISO)

    fun key(date: LocalDate): String = date.format(ISO)

    fun parse(key: String): LocalDate = LocalDate.parse(key, ISO)

    fun now(): Long = System.currentTimeMillis()

    fun hourOfDay(epochMillis: Long = System.currentTimeMillis()): Int =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).hour

    /** 显示用：今天 / 昨天 / M月d日 */
    fun friendlyLabel(key: String): String {
        val date = parse(key)
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> date.format(MONTH_DAY)
        }
    }

    /** 本周（周一为首日）7 天的日期键，从周一到周日 */
    fun currentWeekKeys(today: LocalDate = DateUtil.today): List<String> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map { key(monday.plusDays(it)) }
    }

    /** 上周同期 7 天的日期键 */
    fun lastWeekKeys(today: LocalDate = DateUtil.today): List<String> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map { key(monday.minusDays(7).plusDays(it)) }
    }

    /** 最近 n 天（含今天）的日期键，升序 */
    fun lastNDays(n: Int, today: LocalDate = DateUtil.today): List<String> =
        (n - 1 downTo 0).map { key(today.minusDays(it.toLong())) }

    /** 本周一 ~ 今天 */
    fun weekToDateRange(today: LocalDate = DateUtil.today): Pair<String, String> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return key(monday) to key(today)
    }

    /** 本月 1 号 ~ 今天 */
    fun monthToDateRange(today: LocalDate = DateUtil.today): Pair<String, String> =
        key(today.withDayOfMonth(1)) to key(today)

    fun monthDays(today: LocalDate = DateUtil.today): List<String> =
        (1..today.dayOfMonth).map { key(today.withDayOfMonth(it)) }

    /** 周几的短标签，周一=一 */
    fun weekdayShort(key: String): String {
        val d = parse(key)
        return when (d.dayOfWeek) {
            DayOfWeek.MONDAY -> "一"
            DayOfWeek.TUESDAY -> "二"
            DayOfWeek.WEDNESDAY -> "三"
            DayOfWeek.THURSDAY -> "四"
            DayOfWeek.FRIDAY -> "五"
            DayOfWeek.SATURDAY -> "六"
            DayOfWeek.SUNDAY -> "日"
        }
    }
}
