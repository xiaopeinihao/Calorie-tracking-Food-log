package com.calorietracker.app.ui.quickinput

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.ai.AiCapability
import com.calorietracker.app.data.ai.AiConfigStore
import com.calorietracker.app.data.ai.AiException
import com.calorietracker.app.data.ai.AiFoodExtractor
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.session.MealDraftSession
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickInputViewModel @Inject constructor(
    private val extractor: AiFoodExtractor,
    private val configStore: AiConfigStore,
    private val session: MealDraftSession,
) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _parsing = MutableStateFlow(false)
    val parsing: StateFlow<Boolean> = _parsing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _needsAiConfig = MutableStateFlow(false)
    val needsAiConfig: StateFlow<Boolean> = _needsAiConfig.asStateFlow()

    val aiReady: Boolean get() = configStore.isReady(AiCapability.TEXT)

    fun onTextChange(value: String) {
        _text.value = value
        _error.value = null
    }

    fun appendText(value: String) {
        _text.value = value
        _error.value = null
    }

    /**
     * 解析这句话 → 灌进草稿会话 → 回结果页。
     * 与拍照识别共用同一条解析与取值链路，所以「AI 估的还是成分表来的」标记是一致的。
     */
    fun parse(mealType: MealType, onReady: () -> Unit) {
        val content = _text.value.trim()
        if (content.isEmpty()) {
            _error.value = "先说一句吃了什么，比如「午饭番茄炒蛋配一碗米饭」"
            return
        }
        if (!aiReady) {
            _needsAiConfig.value = true
            _error.value = "还没配置文本模型，去「我的 → AI 设置」填一个"
            return
        }
        viewModelScope.launch {
            _parsing.value = true
            _error.value = null
            try {
                val drafts = extractor.extractFromText(content)
                if (drafts.isEmpty()) {
                    _error.value = "没解析出食物，换个说法再试试"
                    return@launch
                }
                session.startManualFlow(mealType)
                session.setDateKey(DateUtil.todayKey())
                session.setNotice("AI 解析自「$content」")
                session.setItems(drafts)
                onReady()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 取消（用户退出页面 / 协程被取消）不是「解析失败」，原样抛出；
                // 否则 catch (Exception) 会把它转成一条假的错误文案。
                throw e
            } catch (e: AiException) {
                _needsAiConfig.value = e.kind == AiException.Kind.NOT_CONFIGURED ||
                    e.kind == AiException.Kind.UNAUTHORIZED ||
                    e.kind == AiException.Kind.NOT_FOUND
                _error.value = e.userMessage
            } catch (e: Exception) {
                _error.value = "解析失败了，可以改用拍照或手动录入"
            } finally {
                _parsing.value = false
            }
        }
    }

    fun consumeError() {
        _error.value = null
        _needsAiConfig.value = false
    }
}
