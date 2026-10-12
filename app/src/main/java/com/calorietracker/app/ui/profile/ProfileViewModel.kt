package com.calorietracker.app.ui.profile

import android.content.Context
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.ai.AiCapability
import com.calorietracker.app.data.ai.AiConfigStore
import com.calorietracker.app.data.local.entity.DailyGoal
import com.calorietracker.app.data.repository.FoodRepository
import com.calorietracker.app.data.repository.GoalRepository
import com.calorietracker.app.data.repository.MealRepository
import com.calorietracker.app.data.repository.UserPrefsRepository
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

data class ProfileUiState(
    val targetKcal: Int = 1600,
    val targetCarbs: Int = 200,
    val targetProtein: Int = 80,
    val targetFat: Int = 53,
    val bmr: Double? = null,
    val tdee: Double? = null,
    val suggestedKcal: Int? = null,
    val foodCount: Int = 0,
    val message: String? = null,
    val exportUri: String? = null,
    /** AI 识别状态摘要，展示在「AI 设置」入口右侧 */
    val aiVisionStatus: String = "未配置",
    val aiTextStatus: String = "未配置",
    /** 身体数据超出合理区间时的提示（null = 没问题或还没填全） */
    val bodyWarning: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goalRepository: GoalRepository,
    private val mealRepository: MealRepository,
    private val foodRepository: FoodRepository,
    private val aiConfigStore: AiConfigStore,
    val prefs: UserPrefsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val bodyProfile = prefs.profile

    private var loaded = false

    init {
        viewModelScope.launch {
            val goal = goalRepository.currentGoal(DateUtil.todayKey())
            _uiState.value = _uiState.value.copy(
                targetKcal = goal.targetKcal,
                targetCarbs = goal.targetCarbs,
                targetProtein = goal.targetProtein,
                targetFat = goal.targetFat,
                foodCount = foodRepository.count(),
            )
            loaded = true
            refreshDerived()
        }
        viewModelScope.launch { bodyProfile.collect { refreshDerived() } }
        // 从 AI 设置页改完配置回来，这里要跟着变
        viewModelScope.launch {
            combine(aiConfigStore.providers, aiConfigStore.binding) { _, _ -> Unit }
                .collect {
                    _uiState.value = _uiState.value.copy(
                        aiVisionStatus = aiStatus(AiCapability.VISION),
                        aiTextStatus = aiStatus(AiCapability.TEXT),
                    )
                }
        }
    }

    private fun aiStatus(capability: AiCapability): String {
        val provider = aiConfigStore.resolve(capability)
            ?: return "未配置 · 点这里去填 API Key"
        return "${provider.name} · ${provider.modelFor(capability)}"
    }

    /** 拖动滑杆：只改数字，松手才落库，避免每一帧都写数据库 */
    fun setTargetKcal(value: Int) {
        val kcal = value.coerceIn(MIN_TARGET, MAX_TARGET)
        _uiState.value = _uiState.value.copy(
            targetKcal = kcal,
            targetCarbs = (kcal * 0.50 / 4).roundToInt(),
            targetProtein = (kcal * 0.20 / 4).roundToInt(),
            targetFat = (kcal * 0.30 / 9).roundToInt(),
        )
    }

    fun saveTarget() {
        val state = _uiState.value
        viewModelScope.launch {
            goalRepository.saveDefaultGoal(
                DailyGoal(
                    date = DailyGoal.DEFAULT_DATE,
                    targetKcal = state.targetKcal,
                    targetCarbs = state.targetCarbs,
                    targetProtein = state.targetProtein,
                    targetFat = state.targetFat,
                )
            )
            _uiState.value = _uiState.value.copy(message = "目标已保存")
        }
    }

    fun updateBody(
        heightCm: Int? = null,
        weightKg: Double? = null,
        age: Int? = null,
        gender: com.calorietracker.app.data.repository.Gender? = null,
        activityFactor: Double? = null,
    ) {
        val current = prefs.profile.value
        prefs.save(
            current.copy(
                heightCm = heightCm ?: current.heightCm,
                weightKg = weightKg ?: current.weightKg,
                age = age ?: current.age,
                gender = gender ?: current.gender,
                activityFactor = activityFactor ?: current.activityFactor,
            )
        )
        refreshDerived()
    }

    /** 用身高体重算出的推荐值一键设为目标 */
    fun applySuggested() {
        val suggested = prefs.suggestedGoal() ?: return
        _uiState.value = _uiState.value.copy(
            targetKcal = suggested.targetKcal,
            targetCarbs = suggested.targetCarbs,
            targetProtein = suggested.targetProtein,
            targetFat = suggested.targetFat,
            message = "已按身高体重推荐值更新目标",
        )
        viewModelScope.launch { goalRepository.saveDefaultGoal(suggested) }
    }

    /**
     * 导出 CSV：写到 App 的外部私有目录，再通过 FileProvider 分享出去。
     * 不申请任何存储权限（Android 10+ 分区存储下也没必要）。
     */
    fun exportCsv() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                        ?: File(context.filesDir, "exports").apply { mkdirs() }
                    val file = File(dir, "calorie_export_${DateUtil.todayKey()}.csv")
                    file.writeText(mealRepository.exportCsv())
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    file to uri
                }.getOrNull()
            }
            _uiState.value = if (result == null) {
                _uiState.value.copy(message = "导出失败，请稍后重试")
            } else {
                _uiState.value.copy(
                    message = "已导出：${result.first.name}",
                    exportUri = result.second.toString(),
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun refreshDerived() {
        if (!loaded) return
        val profile = prefs.profile.value
        _uiState.value = _uiState.value.copy(
            bmr = prefs.bmr(profile),
            tdee = prefs.tdee(profile),
            suggestedKcal = prefs.suggestedGoalKcal(profile),
            bodyWarning = prefs.bodyProfileIssue(profile),
        )
    }

    val blankState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private companion object {
        const val MIN_TARGET = 1000
        const val MAX_TARGET = 3500
    }
}
