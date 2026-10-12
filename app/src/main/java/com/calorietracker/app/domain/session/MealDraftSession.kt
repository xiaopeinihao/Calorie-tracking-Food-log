package com.calorietracker.app.domain.session

import android.net.Uri
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.model.EntrySource
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** 识别状态机。UI 只能通过它决定显示取景中 / 识别中 / 结果 / 失败态。 */
sealed interface RecognitionState {
    data object Idle : RecognitionState
    data object Recognizing : RecognitionState
    data class Success(val items: List<DraftItem>) : RecognitionState

    /**
     * @param retryable 是否还能重试（无网络时也允许重试，但会同时给手动录入出口）
     * @param needsAiConfig 失败根因是「没配 AI / Key 不对」时置位，界面给「去 AI 设置」入口
     */
    data class Failure(
        val message: String,
        val retryable: Boolean,
        val needsAiConfig: Boolean = false,
    ) : RecognitionState
}

/**
 * 「正在记录的这一餐」的跨页面会话。拍照页 → 结果页 → 手动添加页 共用同一份草稿，
 * 避免把复杂对象塞进导航参数里。
 */
@Singleton
class MealDraftSession @Inject constructor() {

    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri: StateFlow<Uri?> = _photoUri.asStateFlow()

    private val _mealType = MutableStateFlow(MealType.SNACK)
    val mealType: StateFlow<MealType> = _mealType.asStateFlow()

    private val _source = MutableStateFlow(EntrySource.PHOTO)
    val source: StateFlow<EntrySource> = _source.asStateFlow()

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    /** 结果页正在编辑的条目 */
    private val _items = MutableStateFlow<List<DraftItem>>(emptyList())
    val items: StateFlow<List<DraftItem>> = _items.asStateFlow()

    /** 日期键：默认今天，允许结果页改到别的日子（补记昨天的饭很常见） */
    private val _dateKey = MutableStateFlow(DateUtil.todayKey())
    val dateKey: StateFlow<String> = _dateKey.asStateFlow()

    /** 编辑已有一餐时记录其 id；为 null 表示是新增 */
    private val _editingMealId = MutableStateFlow<Long?>(null)
    val editingMealId: StateFlow<Long?> = _editingMealId.asStateFlow()

    val total: NutritionSummary
        get() = NutritionSummary.of(_items.value.map { it.nutrition })

    fun startPhotoFlow(uri: Uri, mealType: MealType) {
        _photoUri.value = uri
        _mealType.value = mealType
        _source.value = EntrySource.PHOTO
        _editingMealId.value = null
        _dateKey.value = DateUtil.todayKey()
        _items.value = emptyList()
        // 新一轮流程要把「等待替换某条」的标记清掉，否则它会跨流程残留，
        // 让下一次普通的「+ 手动添加食物」误替换成上一条（见 beginResolve 注释）
        _resolvingIndex.value = null
        // 同理：上一轮的顶部提示条（如「当前是离线演示识别」「AI 解析自…」）
        // 也会跨流程残留，挂在一次毫不相关的新记录上。
        _notice.value = null
        _state.value = RecognitionState.Recognizing
    }

    fun startManualFlow(mealType: MealType) {
        _photoUri.value = null
        _mealType.value = mealType
        _source.value = EntrySource.MANUAL
        _editingMealId.value = null
        _dateKey.value = DateUtil.todayKey()
        _items.value = emptyList()
        _resolvingIndex.value = null
        // 清掉上一轮遗留的提示条（例如「AI 解析自『…』」）——
        // 否则从首页点一个未记录的餐次进来的结果页上，会挂着上一轮那句毫无关系的提示。
        _notice.value = null
        _state.value = RecognitionState.Success(emptyList())
    }

    fun startEditFlow(mealId: Long, dateKey: String, mealType: MealType, items: List<DraftItem>) {
        _editingMealId.value = mealId
        _dateKey.value = dateKey
        _mealType.value = mealType
        _source.value = EntrySource.MANUAL
        _photoUri.value = null
        _items.value = items
        _resolvingIndex.value = null
        // 编辑历史餐次同样不能带上一轮的提示条
        _notice.value = null
        _state.value = RecognitionState.Success(items)
    }

    fun onRecognizing() {
        _state.value = RecognitionState.Recognizing
    }

    fun onRecognized(items: List<DraftItem>) {
        _items.value = items
        _state.value = if (items.isEmpty()) {
            RecognitionState.Failure("没有识别到食物", retryable = true)
        } else {
            RecognitionState.Success(items)
        }
    }

    fun onRecognitionFailed(message: String, retryable: Boolean = true, needsAiConfig: Boolean = false) {
        _state.value = RecognitionState.Failure(message, retryable, needsAiConfig)
    }

    /** 顶部提示条：例如「当前是离线演示识别」 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun setNotice(text: String?) {
        _notice.value = text
    }

    fun setItems(items: List<DraftItem>) {
        _items.value = items
        if (_state.value is RecognitionState.Success) {
            _state.value = RecognitionState.Success(items)
        }
    }

    fun addItem(item: DraftItem) {
        _items.update { it + item }
        _state.value = RecognitionState.Success(_items.value)
    }

    fun updateGrams(index: Int, grams: Int) {
        val current = _items.value
        if (index !in current.indices) return
        val item = current[index]
        val per100 = if (item.grams > 0) {
            NutritionSummary(
                kcal = item.nutrition.kcal / item.grams * 100,
                carbs = item.nutrition.carbs / item.grams * 100,
                protein = item.nutrition.protein / item.grams * 100,
                fat = item.nutrition.fat / item.grams * 100,
            )
        } else {
            NutritionSummary.EMPTY
        }
        val scaled = NutritionSummary(
            kcal = per100.kcal * grams / 100.0,
            carbs = per100.carbs * grams / 100.0,
            protein = per100.protein * grams / 100.0,
            fat = per100.fat * grams / 100.0,
        )
        // 「营养待补充」的项本来就没有营养值可缩放，改份量不该给它打上「已调整」——
        // 那会让人以为数值被调过，其实一直是缺的。
        val updated = if (item.needsNutrition) {
            item.copy(grams = grams)
        } else {
            item.copy(grams = grams, nutrition = scaled, userEdited = true)
        }
        _items.value = current.toMutableList().also { it[index] = updated }
        _state.value = RecognitionState.Success(_items.value)
    }

    fun removeItem(index: Int) {
        val current = _items.value
        if (index !in current.indices) return
        _items.value = current.toMutableList().also { it.removeAt(index) }
        _state.value = RecognitionState.Success(_items.value)
    }

    /**
     * 当前正在「从食物库补数据」替换的条目下标；null 表示普通新增。
     *
     * 这个标记**必须在离开「选食物」页面或开始新流程时清掉**：
     * 否则用户点了「从食物库补上」→ 又按返回退出 → 之后点普通的「+ 手动添加食物」，
     * 就会把上一条替换掉（用户以为自己是在新增，原来那条却没了）。
     */
    private val _resolvingIndex = MutableStateFlow<Int?>(null)
    val resolvingIndex: StateFlow<Int?> = _resolvingIndex.asStateFlow()

    fun beginResolve(index: Int) {
        _resolvingIndex.value = index.takeIf { it in _items.value.indices }
    }

    fun cancelResolve() {
        _resolvingIndex.value = null
    }

    /** 用食物库里的权威条目替换掉某个「营养待补充」的条目 */
    fun replaceItemAt(index: Int, item: DraftItem) {
        val current = _items.value
        if (index !in current.indices) return
        _items.value = current.toMutableList().also { it[index] = item }
        _state.value = RecognitionState.Success(_items.value)
        _resolvingIndex.value = null
    }

    fun setMealType(type: MealType) {
        _mealType.value = type
    }

    fun setDateKey(key: String) {
        _dateKey.value = key
    }

    fun clear() {
        _photoUri.value = null
        _items.value = emptyList()
        _state.value = RecognitionState.Idle
        _editingMealId.value = null
        _notice.value = null
        _resolvingIndex.value = null
    }
}
