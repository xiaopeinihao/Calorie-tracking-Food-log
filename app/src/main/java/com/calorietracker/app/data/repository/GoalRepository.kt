package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.dao.GoalDao
import com.calorietracker.app.data.local.entity.DailyGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 目标仓库：某天没单独设目标就回落到全局默认目标，再没有就用产品默认值。
 */
@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
) {

    fun observeGoal(date: String): Flow<DailyGoal> =
        combine(goalDao.observe(date), goalDao.observeDefault()) { dayGoal, defaultGoal ->
            dayGoal ?: defaultGoal ?: DailyGoal.default()
        }

    fun observeDefaultGoal(): Flow<DailyGoal> =
        goalDao.observeDefault().map { it ?: DailyGoal.default() }

    suspend fun currentGoal(date: String): DailyGoal =
        goalDao.goalForDate(date) ?: goalDao.goalForDate(DailyGoal.DEFAULT_DATE) ?: DailyGoal.default()

    /**
     * 确保存在一行全局默认目标，**已存在则原样保留**。
     *
     * 这里刻意不用 [GoalDao.upsert]：启动时无条件写一遍，会把用户设过的目标
     * （以及「按身高体重推荐」算出来的值）在每次冷启动覆盖回 1600，
     * 属于必现的数据丢失。用户主动改目标走 [saveGoalForDate]，那条路径仍然是覆盖写。
     */
    suspend fun ensureDefaultGoal(goal: DailyGoal = DailyGoal.default()) =
        goalDao.insertIfAbsent(goal.copy(date = DailyGoal.DEFAULT_DATE))

    /**
     * 用户**主动**改目标时用（「我的 → 目标热量」保存、一键套用推荐值）：这里是覆盖写，
     * 因为覆盖就是用户的意图。
     *
     * 与 [ensureDefaultGoal] 的区别只有这一点，但绝不能混用：启动路径用覆盖写 =
     * 每次冷启动把用户设的值冲掉；用户操作走「缺了才补」也不对（改不动）。所以两个方法留着。
     */
    suspend fun saveDefaultGoal(goal: DailyGoal) =
        goalDao.upsert(goal.copy(date = DailyGoal.DEFAULT_DATE))

    suspend fun saveGoalForDate(goal: DailyGoal) = goalDao.upsert(goal)
}
