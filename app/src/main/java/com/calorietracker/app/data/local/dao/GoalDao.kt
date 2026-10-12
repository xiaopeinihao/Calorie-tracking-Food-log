package com.calorietracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.calorietracker.app.data.local.entity.DailyGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: DailyGoal)

    /**
     * **只在缺失时插入**（已存在就什么都不做）。
     *
     * 专供启动路径写「全局默认目标」用：启动逻辑如果走 [upsert]（REPLACE），
     * 每次冷启动都会把用户自己设过的目标覆盖回产品默认值 1600，
     * 连按身高体重算出来的推荐目标也一起丢 —— 这是必现的数据丢失。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(goal: DailyGoal)

    @Query("SELECT * FROM daily_goals WHERE date = :date LIMIT 1")
    suspend fun goalForDate(date: String): DailyGoal?

    @Query("SELECT * FROM daily_goals WHERE date = :date LIMIT 1")
    fun goalForDateFlow(date: String): Flow<DailyGoal?>

    @Query("SELECT * FROM daily_goals WHERE date = :date LIMIT 1")
    fun observe(date: String): Flow<DailyGoal?>

    /** 全局默认目标行（date = __default__） */
    @Query("SELECT * FROM daily_goals WHERE date = '__default__' LIMIT 1")
    fun observeDefault(): Flow<DailyGoal?>
}
