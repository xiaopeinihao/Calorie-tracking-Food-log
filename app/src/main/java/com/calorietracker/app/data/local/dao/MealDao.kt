package com.calorietracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.calorietracker.app.data.local.entity.EntryItem
import com.calorietracker.app.data.local.entity.MealEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Insert
    suspend fun insertMeal(meal: MealEntry): Long

    @Insert
    suspend fun insertItems(items: List<EntryItem>)

    @Transaction
    suspend fun insertMealWithItems(meal: MealEntry, items: List<EntryItem>): Long {
        val mealId = insertMeal(meal)
        insertItems(items.map { it.copy(mealEntryId = mealId) })
        return mealId
    }

    @Delete
    suspend fun deleteMeal(meal: MealEntry)

    @Query("DELETE FROM entry_items WHERE meal_entry_id = :mealId")
    suspend fun deleteItemsOfMeal(mealId: Long)

    /** 编辑已保存的一餐：整组替换，保证「克数变了，热量跟着变」的口径唯一 */
    @Transaction
    suspend fun replaceItems(mealId: Long, items: List<EntryItem>) {
        deleteItemsOfMeal(mealId)
        insertItems(items.map { it.copy(id = 0, mealEntryId = mealId) })
    }

    @Query("DELETE FROM meal_entries WHERE id = :mealId")
    suspend fun deleteMealById(mealId: Long)

    /** 所有记录引用到的照片（内容为 FileProvider 的 content:// 字符串），供孤儿文件清理比对 */
    @Query("SELECT photo_path FROM meal_entries WHERE photo_path IS NOT NULL")
    suspend fun photoPaths(): List<String>

    /**
     * 编辑一餐：明细整组替换 + 抬头（餐次/日期）更新放进**同一个事务**。
     *
     * 分两次调用（先 [replaceItems] 再 [updateMealHeader]）的话，中间任何一次失败
     * 或进程被杀，都会留下「明细换了、餐次/日期还是旧的」的半提交状态，
     * 而界面上显示的是「已改」——用户以为改了，库里没有。
     */
    @Transaction
    suspend fun updateMealWithItems(
        mealId: Long,
        items: List<EntryItem>,
        mealType: String,
        date: String,
    ) {
        replaceItems(mealId, items)
        updateMealHeader(mealId, mealType, date)
    }

    /**
     * 编辑一餐时同步更新「抬头」（餐次 / 日期）。
     *
     * 明细整组替换只动了 `entry_items`，而结果页是允许改餐次的 ——
     * 只替换明细的话，用户改完餐次点保存，界面上显示改了，实际写库时被丢掉。
     */
    @Query("UPDATE meal_entries SET meal_type = :mealType, date = :date WHERE id = :mealId")
    suspend fun updateMealHeader(mealId: Long, mealType: String, date: String)

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY created_at ASC")
    fun mealsForDate(date: String): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE id = :mealId LIMIT 1")
    fun mealById(mealId: Long): Flow<MealWithItems?>

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE id = :mealId LIMIT 1")
    suspend fun mealByIdOnce(mealId: Long): MealWithItems?

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE date BETWEEN :start AND :end ORDER BY date DESC, created_at ASC")
    fun mealsBetween(start: String, end: String): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal_entries ORDER BY date DESC, created_at ASC")
    fun allMeals(): Flow<List<MealWithItems>>

    /** 所有有记录的日期，倒序 */
    @Query("SELECT DISTINCT date FROM meal_entries ORDER BY date DESC")
    fun distinctDates(): Flow<List<String>>

    /** CSV 导出用的挂起版 */
    @Query("SELECT DISTINCT date FROM meal_entries ORDER BY date DESC")
    suspend fun distinctDatesOnce(): List<String>

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY created_at ASC")
    suspend fun mealsForDateOnce(date: String): List<MealWithItems>

    @Transaction
    @Query("SELECT * FROM meal_entries ORDER BY date DESC, created_at ASC")
    suspend fun allMealsOnce(): List<MealWithItems>
}
