package com.calorietracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.local.dao.MealWithItems
import com.calorietracker.app.data.local.entity.DailyGoal
import com.calorietracker.app.data.repository.GoalRepository
import com.calorietracker.app.data.repository.MealRepository
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.domain.session.MealDraftSession
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首页某一餐的汇总 */
data class MealGroup(
    val type: MealType,
    val kcal: Double,
    val mealId: Long?,
    val itemCount: Int,
)

data class HomeUiState(
    val dateKey: String = DateUtil.todayKey(),
    val summary: NutritionSummary = NutritionSummary.EMPTY,
    val goal: DailyGoal = DailyGoal.default(),
    val groups: List<MealGroup> = emptyList(),
    val streakDays: Int = 0,
    val hasAnyRecord: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val goalRepository: GoalRepository,
    private val session: MealDraftSession,
) : ViewModel() {

    /**
     * 「今天」是会变的：原来这里是个构造时算一次的 val，App 一直开着跨过零点，
     * 首页的「今天」就停在昨天（还会把昨天吃完的饭显示成今天的进度）。
     * 改成每分钟对一次表，日期变了就切换数据源。
     */
    private val dateKeyFlow = MutableStateFlow(DateUtil.todayKey())

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val today = DateUtil.todayKey()
                if (today != dateKeyFlow.value) dateKeyFlow.value = today
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = dateKeyFlow.flatMapLatest { dateKey ->
        combine(
            mealRepository.observeDay(dateKey),
            goalRepository.observeGoal(dateKey),
            mealRepository.observeStreakDays(),
        ) { meals, goal, streak ->
            HomeUiState(
                dateKey = dateKey,
                summary = NutritionSummary.of(meals.map { it.summary }),
                goal = goal,
                groups = buildGroups(meals),
                streakDays = streak,
                hasAnyRecord = meals.isNotEmpty(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * 点某一餐：有记录就进入编辑，没记录就以该餐次开一张空白草稿去手动补。
     */
    fun openMeal(type: MealType, onReady: () -> Unit) {
        viewModelScope.launch {
            val date = DateUtil.todayKey()
            val meals = mealRepository.dayMealsOnce(date)
            val target = meals.firstOrNull { it.meal.mealType == type.label }
            if (target != null) {
                session.startEditFlow(
                    mealId = target.meal.id,
                    dateKey = date,
                    mealType = type,
                    items = target.items.map { it.toDraft() },
                )
            } else {
                session.startManualFlow(type)
            }
            onReady()
        }
    }

    private fun buildGroups(meals: List<MealWithItems>): List<MealGroup> =
        MealType.entries.map { type ->
            val matched = meals.filter { it.meal.mealType == type.label }
            MealGroup(
                type = type,
                kcal = matched.sumOf { it.totalKcal },
                mealId = matched.firstOrNull()?.meal?.id,
                itemCount = matched.sumOf { it.items.size },
            )
        }

    private companion object {
        /** 对表间隔：跨零点的感知延迟最多 1 分钟，代价可忽略 */
        const val TICK_MS = 60_000L
    }
}

internal fun com.calorietracker.app.data.local.entity.EntryItem.toDraft(): DraftItem = DraftItem(
    foodId = foodId,
    foodName = foodName,
    grams = grams,
    nutrition = NutritionSummary(kcal = kcal, carbs = carbs, protein = protein, fat = fat),
    userEdited = userEdited,
    // 必须带上原来的出处：编辑保存时 source 会写回 entry_items.source_label，
    // 传 null 会把「来自成分表 / AI 估算」的标记在每次编辑后抹掉（CSV 导出与详情都靠它）
    source = sourceLabel,
    // 明细表里没有单独的「AI 估算」字段，靠出处标签判断（AI 那条存的是「AI 估算 · 模型名」）
    aiEstimated = sourceLabel?.startsWith(AI_SOURCE_PREFIX) == true,
)

/** AI 估算条目的出处标签前缀，落库与识别时都用它 */
internal const val AI_SOURCE_PREFIX = "AI 估算"
