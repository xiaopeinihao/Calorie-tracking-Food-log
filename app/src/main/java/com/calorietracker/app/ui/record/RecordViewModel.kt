package com.calorietracker.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.local.dao.MealWithItems
import com.calorietracker.app.data.repository.MealRepository
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.domain.session.MealDraftSession
import com.calorietracker.app.ui.home.toDraft
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 一天的分组 */
data class RecordDay(
    val dateKey: String,
    val label: String,
    val summary: NutritionSummary,
    val meals: List<MealWithItems>,
)

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val session: MealDraftSession,
) : ViewModel() {

    val days: StateFlow<List<RecordDay>> = mealRepository.observeAll()
        .map { meals ->
            meals.groupBy { it.meal.date }
                .map { (date, list) ->
                    RecordDay(
                        dateKey = date,
                        label = DateUtil.friendlyLabel(date),
                        summary = NutritionSummary.of(list.map { it.summary }),
                        meals = list.sortedBy { it.meal.createdAt },
                    )
                }
                .sortedByDescending { it.dateKey }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    init {
        // 默认展开今天
        _expanded.value = setOf(DateUtil.todayKey())
    }

    fun toggle(dateKey: String) {
        val current = _expanded.value
        _expanded.value = if (current.contains(dateKey)) current - dateKey else current + dateKey
    }

    fun delete(mealId: Long) {
        viewModelScope.launch { mealRepository.deleteMeal(mealId) }
    }

    /** 点击一条记录进入编辑：把它的明细灌进草稿会话 */
    fun beginEdit(mealId: Long, onReady: () -> Unit) {
        viewModelScope.launch {
            val meals = mealRepository.observeAllOnce()
            val target = meals.firstOrNull { it.meal.id == mealId } ?: return@launch
            session.startEditFlow(
                mealId = mealId,
                dateKey = target.meal.date,
                mealType = MealType.fromLabel(target.meal.mealType),
                items = target.items.map { it.toDraft() },
            )
            onReady()
        }
    }
}
