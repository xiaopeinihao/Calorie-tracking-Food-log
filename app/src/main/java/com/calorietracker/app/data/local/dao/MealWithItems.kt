package com.calorietracker.app.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.calorietracker.app.data.local.entity.EntryItem
import com.calorietracker.app.data.local.entity.MealEntry
import com.calorietracker.app.domain.nutrition.NutritionSummary

/** 一条记录 + 其下所有食物项 */
data class MealWithItems(
    @Embedded val meal: MealEntry,
    @Relation(parentColumn = "id", entityColumn = "meal_entry_id")
    val items: List<EntryItem>,
) {
    val totalKcal: Double get() = items.sumOf { it.kcal }
    val totalCarbs: Double get() = items.sumOf { it.carbs }
    val totalProtein: Double get() = items.sumOf { it.protein }
    val totalFat: Double get() = items.sumOf { it.fat }

    /** 与全工程统一的营养汇总模型对接 */
    val summary: NutritionSummary get() = NutritionSummary(
        kcal = totalKcal,
        carbs = totalCarbs,
        protein = totalProtein,
        fat = totalFat,
    )
}
