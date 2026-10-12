package com.calorietracker.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.ai.AiCapability
import com.calorietracker.app.data.ai.AiClient
import com.calorietracker.app.data.ai.AiConfigStore
import com.calorietracker.app.data.ai.AiProviderConfig
import com.calorietracker.app.data.ai.AiTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val store: AiConfigStore,
    private val client: AiClient,
) : ViewModel() {

    val providers: StateFlow<List<AiProviderConfig>> = store.providers
    val binding get() = store.binding

    /** 正在展开编辑的供应商 id（手风琴，同时只开一个） */
    private val _expandedId = MutableStateFlow<String?>(null)
    val expandedId: StateFlow<String?> = _expandedId.asStateFlow()

    /** 展开项的草稿：测试与保存都针对草稿，改完再存 */
    private val _draft = MutableStateFlow<AiProviderConfig?>(null)
    val draft: StateFlow<AiProviderConfig?> = _draft.asStateFlow()

    /** 测试结果：key = 能力名 */
    private val _tests = MutableStateFlow<Map<AiCapability, AiTestResult>>(emptyMap())
    val tests: StateFlow<Map<AiCapability, AiTestResult>> = _tests.asStateFlow()

    private val _testing = MutableStateFlow<Set<AiCapability>>(emptySet())
    val testing: StateFlow<Set<AiCapability>> = _testing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun expand(providerId: String) {
        if (_expandedId.value == providerId) return
        _expandedId.value = providerId
        _tests.value = emptyMap()
        _draft.value = store.provider(providerId)
    }

    fun collapse() {
        _expandedId.value = null
        _draft.value = null
        _tests.value = emptyMap()
    }

    fun updateDraft(transform: (AiProviderConfig) -> AiProviderConfig) {
        _draft.update { current -> current?.let(transform) }
    }

    fun saveDraft() {
        val config = _draft.value ?: return
        store.upsert(config)
        _message.value = "已保存「${config.name.ifBlank { "未命名" }}」"
    }

    fun delete(providerId: String) {
        val target = store.provider(providerId) ?: return
        if (target.isBuiltIn) {
            _message.value = "预设服务商不能删除，可以直接改配置"
            return
        }
        store.delete(providerId)
        if (_expandedId.value == providerId) collapse()
        _message.value = "已删除「${target.name}」"
    }

    fun addCustomProvider() {
        val index = store.providers.value.count { !it.isBuiltIn } + 1
        val config = AiProviderConfig(
            id = "custom-" + System.currentTimeMillis().toString(36),
            name = "我的服务商 $index",
            baseUrl = "",
        )
        store.upsert(config)
        expand(config.id)
    }

    fun bind(capability: AiCapability, providerId: String?) {
        store.bind(capability, providerId)
        val name = providerId?.let { store.provider(it)?.name } ?: "未绑定"
        _message.value = "${capability.label}能力已切到「$name」"
    }

    fun test(capability: AiCapability) {
        val config = _draft.value ?: return
        viewModelScope.launch {
            _testing.update { it + capability }
            val result = client.test(config, capability)
            _tests.update { it + (capability to result) }
            _testing.update { it - capability }
        }
    }

    fun testAll() {
        val config = _draft.value ?: return
        viewModelScope.launch {
            val capabilities = AiCapability.entries.filter { config.modelFor(it).isNotBlank() }
            if (capabilities.isEmpty()) {
                _message.value = "先把模型名填上再测试"
                return@launch
            }
            _testing.update { it + capabilities }
            capabilities.forEach { capability ->
                val result = client.test(config, capability)
                _tests.update { it + (capability to result) }
                _testing.update { it - capability }
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 当前能力实际会走哪家 */
    fun effectiveProvider(capability: AiCapability): AiProviderConfig? = store.resolve(capability)
}
