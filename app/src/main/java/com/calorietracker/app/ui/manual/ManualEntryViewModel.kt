package com.calorietracker.app.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.local.entity.FoodItem
import com.calorietracker.app.data.repository.FoodRepository
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.domain.session.MealDraftSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val session: MealDraftSession,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<FoodItem?>(null)
    val selected: StateFlow<FoodItem?> = _selected.asStateFlow()

    private val _grams = MutableStateFlow(100)
    val grams: StateFlow<Int> = _grams.asStateFlow()

    /** 正在加入本餐：用来禁用按钮，防止连点加两条 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val results: StateFlow<List<FoodItem>> = _query
        .flatMapLatest { foodRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun select(food: FoodItem) {
        _selected.value = food
        // 用该食物的常见分量作为默认值，减少用户输入
        _grams.value = food.commonServingGrams.coerceAtLeast(10)
    }

    fun clearSelection() {
        _selected.value = null
    }

    fun setGrams(value: Int) {
        _grams.value = value.coerceIn(MIN_GRAMS, MAX_GRAMS)
    }

    /** 实时热量：始终是「权威每 100g 值 × 克数」，不在这里另算营养 */
    fun currentSummary(): NutritionSummary {
        val food = _selected.value ?: return NutritionSummary.EMPTY
        return foodRepository.toSummary(food, _grams.value)
    }

    /**
     * 把当前选择加入本餐。
     *
     * [onAdded] 必须在这一项**真的进了会话之后**才回调：页面一弹出，
     * 这个 ViewModel 就会被销毁、viewModelScope 被取消，
     * 若先导航再写会话，协程会被cancel掉，用户刚选的食物就没了。
     */
    fun confirm(onAdded: () -> Unit) {
        val food = _selected.value ?: return
        // 防连点：每次调用都会往会话里加一条，连点两次就会加两条，
        // 而且 onAdded 会被回调两次（第二次可能又压一层结果页）
        if (_busy.value) return
        _busy.value = true
        val gramsValue = _grams.value
        viewModelScope.launch {
            // 先查一次库里的最新值再换算，保证用的是落库的权威数值
            val fresh = foodRepository.findById(food.id) ?: food
            val draft = com.calorietracker.app.domain.model.DraftItem(
                foodId = fresh.id,
                foodName = fresh.name,
                grams = gramsValue,
                nutrition = foodRepository.toSummary(fresh, gramsValue),
                userEdited = true,
                source = fresh.source,
            )
            // 如果是从结果页点「从食物库补上」进来的，就替换那一条，而不是新增
            val resolving = session.resolvingIndex.value
            if (resolving != null) {
                session.replaceItemAt(resolving, draft)
            } else {
                session.addItem(draft)
            }
            onAdded()
        }
    }

    /**
     * 用户按返回离开本页：清掉「替换某条」的标记。
     * 不清的话，下次普通的「+ 手动添加食物」会误把上一条替换掉。
     */
    fun cancelResolve() {
        session.cancelResolve()
    }

    private companion object {
        const val MIN_GRAMS = 5
        const val MAX_GRAMS = 2000
    }
}
