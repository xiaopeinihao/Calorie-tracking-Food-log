package com.calorietracker.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.repository.GoalRepository
import com.calorietracker.app.data.repository.MealRepository
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StatPeriod(val label: String) {
    DAY("日"), WEEK("周"), MONTH("月")
}

data class StatsUiState(
    val period: StatPeriod = StatPeriod.WEEK,
    val headlineLabel: String = "本周日均摄入",
    val headlineKcal: Double = 0.0,
    val deltaLabel: String = "较上周",
    val deltaPercent: Int? = null,
    val chartDates: List<String> = emptyList(),
    val chartValues: List<Double> = emptyList(),
    val chartLabels: List<String> = emptyList(),
    val highlightIndex: Int = -1,
    val targetKcal: Int = 1600,
    val macro: NutritionSummary = NutritionSummary.EMPTY,
    val streakDays: Int = 0,
    val hasData: Boolean = false,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(StatPeriod.WEEK)
    val period: StateFlow<StatPeriod> = _period.asStateFlow()

    /**
     * 每分钟对一次表，只在新的一天到来时发射。
     *
     * 统计页此前完全没有时间感知：停在趋势页过零点，只要没有新的数据库写入，
     * 「今日摄入 / 本周」就会一直停在昨天的口径（首页早已做了同样的对表，这里补上）。
     */
    private val todayTick: Flow<String> = flow {
        while (true) {
            emit(DateUtil.todayKey())
            delay(60_000L)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<StatsUiState> = combine(
        _period,
        mealRepository.observeAll(),
        goalRepository.observeDefaultGoal(),
        mealRepository.observeStreakDays(),
        todayTick,
    ) { period, meals, goal, streak, today ->
        val byDate: Map<String, NutritionSummary> = meals
            .groupBy { it.meal.date }
            .mapValues { (_, list) -> NutritionSummary.of(list.map { it.summary }) }

        fun kcalOf(date: String): Double = byDate[date]?.kcal ?: 0.0
        fun sumOf(dates: List<String>): NutritionSummary =
            NutritionSummary.of(dates.map { byDate[it] ?: NutritionSummary.EMPTY })

        fun average(dates: List<String>): Double =
            if (dates.isEmpty()) 0.0 else dates.sumOf { kcalOf(it) } / dates.size

        when (period) {
            StatPeriod.DAY -> {
                val dates = DateUtil.lastNDays(7)
                val today = DateUtil.todayKey()
                val yesterday = DateUtil.key(DateUtil.today.minusDays(1))
                val current = kcalOf(today)
                val previous = kcalOf(yesterday)
                StatsUiState(
                    period = period,
                    headlineLabel = "今日摄入",
                    headlineKcal = current,
                    deltaLabel = "较昨天",
                    deltaPercent = NutritionMath.periodOverPeriodChange(current, previous)
                        ?.let { Math.round(it * 100).toInt() },
                    chartDates = dates,
                    chartValues = dates.map { kcalOf(it) },
                    chartLabels = dates.map { DateUtil.weekdayShort(it) },
                    highlightIndex = dates.indexOf(today),
                    targetKcal = goal.targetKcal,
                    macro = byDate[today] ?: NutritionSummary.EMPTY,
                    streakDays = streak,
                    hasData = current > 0,
                )
            }

            StatPeriod.WEEK -> {
                val week = DateUtil.currentWeekKeys()
                val todayIndex = week.indexOf(DateUtil.todayKey()).takeIf { it >= 0 } ?: 6
                val elapsed = week.take(todayIndex + 1)
                val lastWeek = DateUtil.lastWeekKeys().take(todayIndex + 1)
                val current = average(elapsed)
                val previous = average(lastWeek)
                StatsUiState(
                    period = period,
                    headlineLabel = "本周日均摄入",
                    headlineKcal = current,
                    deltaLabel = "较上周",
                    deltaPercent = NutritionMath.periodOverPeriodChange(current, previous)
                        ?.let { Math.round(it * 100).toInt() },
                    chartDates = week,
                    chartValues = week.map { kcalOf(it) },
                    chartLabels = week.map { DateUtil.weekdayShort(it) },
                    highlightIndex = highlight(week.map { kcalOf(it) }),
                    targetKcal = goal.targetKcal,
                    macro = sumOf(week),
                    streakDays = streak,
                    hasData = current > 0,
                )
            }

            StatPeriod.MONTH -> {
                val month = DateUtil.monthDays()
                val current = average(month)
                StatsUiState(
                    period = period,
                    headlineLabel = "本月日均摄入",
                    headlineKcal = current,
                    deltaLabel = "较上月",
                    deltaPercent = null, // 上月同期口径需完整历史数据，暂不展示以免误导
                    chartDates = month,
                    chartValues = month.map { kcalOf(it) },
                    chartLabels = month.mapIndexed { index, _ ->
                        if (index % 5 == 0) (index + 1).toString() else ""
                    },
                    highlightIndex = highlight(month.map { kcalOf(it) }),
                    targetKcal = goal.targetKcal,
                    macro = sumOf(month),
                    streakDays = streak,
                    hasData = current > 0,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectPeriod(period: StatPeriod) {
        _period.value = period
    }

    private fun highlight(values: List<Double>): Int {
        val max = values.maxOrNull() ?: 0.0
        return if (max <= 0.0) -1 else values.indexOf(max)
    }
}
