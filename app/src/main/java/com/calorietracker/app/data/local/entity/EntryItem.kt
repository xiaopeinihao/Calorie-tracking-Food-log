package com.calorietracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一餐中的一项食物。克数是唯一「可变输入」，
 * 热量与三大营养素都是按 [grams] × 每 100g 值算出后落库的快照，
 * 这样即便日后食物库数值调整，历史记录也不会被改写。
 */
@Entity(
    tableName = "entry_items",
    foreignKeys = [
        ForeignKey(
            entity = MealEntry::class,
            parentColumns = ["id"],
            childColumns = ["meal_entry_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("meal_entry_id")],
)
data class EntryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_entry_id") val mealEntryId: Long,
    /** 关联食物库 id，可空（完全手写的一条可以没有库 id） */
    @ColumnInfo(name = "food_id") val foodId: String? = null,
    @ColumnInfo(name = "food_name") val foodName: String,
    @ColumnInfo(name = "grams") val grams: Int,
    @ColumnInfo(name = "kcal") val kcal: Double,
    @ColumnInfo(name = "carbs") val carbs: Double,
    @ColumnInfo(name = "protein") val protein: Double,
    @ColumnInfo(name = "fat") val fat: Double,
    /** 用户是否手动改过份量（用于结果页打标记） */
    @ColumnInfo(name = "user_edited") val userEdited: Boolean = false,
    /**
     * 数据出处快照：权威库名，或「AI 估算 · 模型名」。
     * 落库时记下来，导出 CSV 与历史详情里才能回溯每个数字是怎么来的。
     */
    @ColumnInfo(name = "source_label") val sourceLabel: String? = null,
)
