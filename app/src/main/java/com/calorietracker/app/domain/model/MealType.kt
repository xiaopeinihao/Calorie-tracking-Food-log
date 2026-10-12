package com.calorietracker.app.domain.model

/** 餐次 */
enum class MealType(val label: String, val order: Int) {
    BREAKFAST("早餐", 0),
    LUNCH("午餐", 1),
    DINNER("晚餐", 2),
    SNACK("加餐", 3);

    companion object {
        fun fromLabel(label: String): MealType =
            entries.firstOrNull { it.label == label } ?: SNACK

        /** Pick a sensible default meal by time of day. */
        fun byHour(hour: Int): MealType = when (hour) {
            in 5..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
    }
}

/** 识别来源 */
enum class EntrySource(val label: String) {
    PHOTO("拍照"),
    MANUAL("手动");

    companion object {
        fun fromLabel(label: String): EntrySource =
            entries.firstOrNull { it.label == label } ?: MANUAL
    }
}
