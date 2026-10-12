package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.dao.MealDao
import com.calorietracker.app.data.local.dao.MealWithItems
import com.calorietracker.app.data.local.entity.EntryItem
import com.calorietracker.app.data.local.entity.MealEntry
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.model.EntrySource
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.domain.nutrition.StreakCalculator
import com.calorietracker.app.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记录仓库。UI 层不直接碰 DAO —— 写库、汇总、连续打卡天数计算都收在这里。
 */
@Singleton
class MealRepository @Inject constructor(
    private val mealDao: MealDao,
) {

    fun observeDay(date: String): Flow<List<MealWithItems>> = mealDao.mealsForDate(date)

    fun observeAll(): Flow<List<MealWithItems>> = mealDao.allMeals()

    fun observeBetween(start: String, end: String): Flow<List<MealWithItems>> =
        mealDao.mealsBetween(start, end)

    /** 某天的营养汇总（首页圆环、报表天粒度都用它） */
    fun observeDaySummary(date: String): Flow<NutritionSummary> =
        mealDao.mealsForDate(date).map { meals -> NutritionSummary.of(meals.map { it.summary }) }

    /** 多天的逐日汇总，保证没有记录的那天也有 0 值行（图形不会缺柱） */
    fun observeRangeDailySummary(dates: List<String>): Flow<Map<String, NutritionSummary>> {
        // 空列表会让 first()/last() 直接抛 NoSuchElementException
        if (dates.isEmpty()) return flowOf(emptyMap())
        return mealDao.mealsBetween(dates.first(), dates.last()).map { meals ->
            val grouped = meals.groupBy { it.meal.date }
            dates.associateWith { date ->
                NutritionSummary.of(grouped[date].orEmpty().map { it.summary })
            }
        }
    }

    /**
     * 最近 [days] 个自然日里有记录的天数。
     *
     * 注意口径：是「自然日区间内记了几天」，不是「最近 N 个有记录的日期」
     * （后者会把三个月前记的两天也算进「最近 30 天」）。
     */
    fun observeLoggedDayCount(days: Int = 30): Flow<Int> {
        val end = DateUtil.key(DateUtil.today)
        val start = DateUtil.key(DateUtil.today.minusDays((days - 1).toLong()))
        return mealDao.distinctDates().map { dates ->
            dates.count { it in start..end }
        }
    }

    /**
     * 连续记录天数。口径见 [com.calorietracker.app.domain.nutrition.StreakCalculator]。
     */
    fun observeStreakDays(): Flow<Int> = mealDao.distinctDates().map { dates ->
        StreakCalculator.streakOf(dates, DateUtil.today)
    }

    suspend fun saveMeal(
        date: String,
        mealType: MealType,
        source: EntrySource,
        photoPath: String?,
        items: List<DraftItem>,
        createdAt: Long = DateUtil.now(),
    ): Long {
        require(items.isNotEmpty()) { "一餐至少要有一项食物" }
        val meal = MealEntry(
            date = date,
            createdAt = createdAt,
            mealType = mealType.label,
            source = source.label,
            photoPath = photoPath,
        )
        return mealDao.insertMealWithItems(meal, items.map { it.toEntryItem(mealId = 0) })
    }

    /** 编辑已保存的一餐：整组替换明细 */
    suspend fun updateMealItems(mealId: Long, items: List<DraftItem>) {
        require(items.isNotEmpty()) { "一餐至少要有一项食物" }
        mealDao.replaceItems(mealId, items.map { it.toEntryItem(mealId) })
    }

    /** 编辑时同步这一餐的餐次与日期（结果页允许改，不能只改明细） */
    suspend fun updateMealHeader(mealId: Long, mealType: MealType, date: String) =
        mealDao.updateMealHeader(mealId, mealType.label, date)

    /**
     * 编辑一餐（**事务版**）：明细 + 餐次 + 日期一次写完。
     *
     * 结果页保存编辑时请走这个，不要自己串 [updateMealItems] + [updateMealHeader]：
     * 拆成两步写，中途失败会留下「明细已换、餐次没换」的半提交。
     */
    suspend fun updateMealWithItems(
        mealId: Long,
        mealType: MealType,
        date: String,
        items: List<DraftItem>,
    ) {
        require(items.isNotEmpty()) { "一餐至少要有一项食物" }
        mealDao.updateMealWithItems(mealId, items.map { it.toEntryItem(mealId) }, mealType.label, date)
    }

    suspend fun deleteMeal(mealId: Long) = mealDao.deleteMealById(mealId)

    /** 一次性读取某天的记录（编辑入口用，不订阅变化） */
    suspend fun dayMealsOnce(date: String): List<MealWithItems> = mealDao.mealsForDateOnce(date)

    /** 一次性读取全部记录（编辑入口用） */
    suspend fun observeAllOnce(): List<MealWithItems> = mealDao.allMealsOnce()

    /**
     * 导出 CSV。含每条明细与每日汇总两块，方便直接丢进 Excel 核对。
     */
    suspend fun exportCsv(): String {
        val builder = StringBuilder()
        builder.append("日期,餐次,来源,食物,克数,热量(kcal),碳水(g),蛋白质(g),脂肪(g),数据来源\n")
        mealDao.distinctDatesOnce().sorted().forEach { date ->
            mealDao.mealsForDateOnce(date).forEach { meal ->
                meal.items.forEach { item ->
                    builder.append(
                        listOf(
                            date,
                            meal.meal.mealType,
                            meal.meal.source,
                            item.foodName,
                            item.grams.toString(),
                            fmt(item.kcal),
                            fmt(item.carbs),
                            fmt(item.protein),
                            fmt(item.fat),
                            // 出处优先用落库时记的标签（能区分「成分表」与「AI 估算」），
                            // 老数据没有标签就回落到食物库 id
                            item.sourceLabel ?: item.foodId.orEmpty(),
                        ).joinToString(",")
                    ).append('\n')
                }
            }
        }
        return builder.toString()
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
}

internal fun DraftItem.toEntryItem(mealId: Long): EntryItem = EntryItem(
    mealEntryId = mealId,
    foodId = foodId,
    foodName = foodName,
    grams = grams,
    kcal = kcal,
    carbs = carbs,
    protein = protein,
    fat = fat,
    userEdited = userEdited,
    // 出处快照：以后翻记录能看出这个数字是成分表来的还是 AI 估的
    sourceLabel = source,
)
