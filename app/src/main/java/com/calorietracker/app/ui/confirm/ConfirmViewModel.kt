package com.calorietracker.app.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.repository.FoodRepository
import com.calorietracker.app.data.repository.MealRepository
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.model.EntrySource
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.domain.session.MealDraftSession
import com.calorietracker.app.domain.session.RecognitionState
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConfirmEvent {
    data object Saved : ConfirmEvent
    data object Deleted : ConfirmEvent
}

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val foodRepository: FoodRepository,
    private val session: MealDraftSession,
) : ViewModel() {

    val items: StateFlow<List<DraftItem>> = session.items
    val state: StateFlow<RecognitionState> = session.state
    val mealType: StateFlow<MealType> = session.mealType
    val photoUri = session.photoUri
    val editingMealId: StateFlow<Long?> = session.editingMealId
    val dateKey: StateFlow<String> = session.dateKey

    private val _events = Channel<ConfirmEvent>(Channel.BUFFERED)
    val events: Flow<ConfirmEvent> = _events.receiveAsFlow()

    /** 正在写库（保存/删除）：用来禁用按钮，避免连点写两条 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val total: NutritionSummary get() = session.total

    /** 改份量：单位热量不变，克数变，热量与营养素按比例重算 */
    fun setGrams(index: Int, grams: Int) = session.updateGrams(index, grams)

    fun remove(index: Int) = session.removeItem(index)

    fun setMealType(type: MealType) = session.setMealType(type)

    /** 结果页顶部提示：识别来源（AI / 离线演示 / 一句话解析） */
    val notice: StateFlow<String?> = session.notice

    fun dismissNotice() = session.setNotice(null)

    /**
     * 某条「营养待补充」的条目：标记它等待替换，然后打开食物库让用户挑。
     * 挑完由 ManualEntryViewModel 调 [MealDraftSession.replaceItemAt] 填回来。
     */
    fun beginResolve(index: Int, onOpenManual: () -> Unit) {
        session.beginResolve(index)
        onOpenManual()
    }

    /**
     * 把 AI 估算的这条存进本地食物库，下次搜索就能直接找到。
     * 存进去的条目会带 aiEstimated 标记，界面上与权威成分表分开显示。
     */
    fun saveToLibrary(index: Int, onDone: (String) -> Unit) {
        val item = session.items.value.getOrNull(index) ?: return
        if (!item.aiEstimated || item.nutrition.kcal <= 0) {
            onDone("这条不用存：营养值来自权威食物库")
            return
        }
        viewModelScope.launch {
            val per100 = NutritionSummary(
                kcal = item.nutrition.kcal / item.grams * 100,
                carbs = item.nutrition.carbs / item.grams * 100,
                protein = item.nutrition.protein / item.grams * 100,
                fat = item.nutrition.fat / item.grams * 100,
            )
            val saved = foodRepository.saveAiEstimated(
                name = item.foodName,
                per100g = per100,
                source = item.source ?: "AI 估算",
            )
            if (saved != null) {
                // 存库后这一条就变成了「有权威 id」的条目，顺带把结果页的标记也更新掉
                session.replaceItemAt(
                    index,
                    item.copy(foodId = saved.id, aiEstimated = false, source = saved.source),
                )
                onDone("已存入「我的食物」，以后搜「${saved.name}」就能找到")
            } else {
                onDone("这条暂时存不了")
            }
        }
    }

    /** 手动补一条：仍然从权威食物库取每 100g 值，绝不由 UI 层估数 */
    fun addFood(foodId: String, grams: Int) {
        viewModelScope.launch {
            val food = foodRepository.findById(foodId) ?: return@launch
            session.addItem(
                DraftItem(
                    foodId = food.id,
                    foodName = food.name,
                    grams = grams,
                    nutrition = foodRepository.toSummary(food, grams),
                    userEdited = true,
                    source = food.source,
                )
            )
        }
    }

    /**
     * 保存这一餐。
     *
     * **必须防连点**：写库是异步的，而 `session.clear()` 在写完之后才执行，
     * 所以两次快速点击都会读到 `editingMealId == null` 并各自插入一条 —— 用户记一次饭，
     * 首页却多出两条一模一样的记录。这里用 [_busy] 把按钮和写库都串起来。
     */
    fun save() {
        val current = session.items.value
        if (current.isEmpty() || _busy.value) return
        // 「营养待补充」的项**绝不能带着占位值 0 进库**：
        // 那会让当天合计永久少算，记录页从此显示一个假的「0 千卡」。
        // 先让用户补上（从食物库挑）或直接删掉这一条。
        val pending = current.count { it.needsNutrition }
        if (pending > 0) {
            session.setNotice(
                "还有 $pending 项没补营养值（显示为「待补充」）：点「从食物库补上」，或删掉这一条再保存",
            )
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                val mealId = session.editingMealId.value
                if (mealId != null) {
                    // 明细 + 餐次/日期在**同一个事务**里写完。
                    // 以前是两步（updateMealItems 然后 updateMealHeader），
                    // 中途失败/进程被杀会留下「明细已换、餐次还是旧的」的半提交，
                    // 而界面显示的是已改 —— 用户以为改了，库里没有。
                    mealRepository.updateMealWithItems(
                        mealId = mealId,
                        mealType = session.mealType.value,
                        date = session.dateKey.value.ifBlank { DateUtil.todayKey() },
                        items = current,
                    )
                    _events.trySend(ConfirmEvent.Saved)
                } else {
                    val date = session.dateKey.value.ifBlank { DateUtil.todayKey() }
                    mealRepository.saveMeal(
                        date = date,
                        mealType = session.mealType.value,
                        source = session.source.value.takeIf { it == EntrySource.MANUAL } ?: EntrySource.PHOTO,
                        photoPath = session.photoUri.value?.toString(),
                        items = current,
                    )
                    _events.trySend(ConfirmEvent.Saved)
                }
                session.clear()
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteExistingMeal() {
        val mealId = session.editingMealId.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                mealRepository.deleteMeal(mealId)
                session.clear()
                _events.trySend(ConfirmEvent.Deleted)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * 进入「+ 手动添加食物」。会先清掉「替换某条」的标记 ——
     * 否则用户上次点了「从食物库补上」又返回退出，这次普通的添加会误替换掉那一条。
     */
    fun addManual(onOpenManual: () -> Unit) {
        session.cancelResolve()
        onOpenManual()
    }
}
