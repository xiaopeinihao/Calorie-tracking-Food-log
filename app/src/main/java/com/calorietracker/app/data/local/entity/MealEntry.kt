package com.calorietracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次记录（一餐或一次加餐）。热量不冗余存储在这里，
 * 而是由其下的 [EntryItem] 汇总得出，避免不一致。
 */
@Entity(
    tableName = "meal_entries",
    indices = [Index("date")],
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 记录归属日期，格式 yyyy-MM-dd（本地时区），用于按天分组与查询 */
    @ColumnInfo(name = "date") val date: String,
    /** 记录的具体时间戳（epoch millis），用于同日内排序 */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** 餐次 label：早餐/午餐/晚餐/加餐 */
    @ColumnInfo(name = "meal_type") val mealType: String,
    /** 来源 label：拍照/手动 */
    @ColumnInfo(name = "source") val source: String,
    /** 拍摄照片的本地路径，可空 */
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
)
