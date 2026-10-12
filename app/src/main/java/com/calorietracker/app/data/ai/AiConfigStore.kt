package com.calorietracker.app.data.ai

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 配置持久化。与 BeeCount 一样存在本地（SharedPreferences + JSON），
 * Key 只在本机保存，且只在调用你选定的服务商时随请求发出。
 */
@Singleton
class AiConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<AiProviderConfig>> = _providers.asStateFlow()

    private val _binding = MutableStateFlow(loadBinding())
    val binding: StateFlow<AiBinding> = _binding.asStateFlow()

    // ---------------------------------------------------------------- 读

    private fun loadProviders(): List<AiProviderConfig> {
        val raw = prefs.getString(KEY_PROVIDERS, null)
        val builtIn = AiPresets.defaults()
        if (raw.isNullOrBlank()) return builtIn
        val saved = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> parse(array.getJSONObject(index)) }
        }.getOrElse { emptyList() }

        // 预设始终保留；用户改过的用用户版本，新增的预设自动补进来
        val byId = saved.associateBy { it.id }
        val merged = builtIn.map { preset -> byId[preset.id] ?: preset } +
            saved.filter { config -> builtIn.none { it.id == config.id } }
        return merged
    }

    private fun parse(json: JSONObject) = AiProviderConfig(
        id = json.optString("id"),
        name = json.optString("name"),
        baseUrl = json.optString("baseUrl"),
        apiKey = json.optString("apiKey"),
        textModel = json.optString("textModel"),
        visionModel = json.optString("visionModel"),
        isBuiltIn = json.optBoolean("isBuiltIn"),
        consoleUrl = json.optString("consoleUrl"),
        hint = json.optString("hint"),
    )

    private fun loadBinding(): AiBinding {
        val raw = prefs.getString(KEY_BINDING, null) ?: return AiBinding()
        return runCatching {
            val json = JSONObject(raw)
            AiBinding(
                textProviderId = json.optString("text").takeIf { it.isNotEmpty() },
                visionProviderId = json.optString("vision").takeIf { it.isNotEmpty() },
            )
        }.getOrElse { AiBinding() }
    }

    // ---------------------------------------------------------------- 写

    fun upsert(config: AiProviderConfig) {
        val current = _providers.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) current[index] = config else current += config
        _providers.value = current
        persistProviders(current)
    }

    fun delete(id: String) {
        val target = _providers.value.firstOrNull { it.id == id } ?: return
        if (target.isBuiltIn) return
        val next = _providers.value.filterNot { it.id == id }
        _providers.value = next
        persistProviders(next)
        // 解绑被删掉的供应商
        val binding = _binding.value
        _binding.value = binding.copy(
            textProviderId = binding.textProviderId?.takeIf { it != id },
            visionProviderId = binding.visionProviderId?.takeIf { it != id },
        )
        persistBinding(_binding.value)
    }

    fun bind(capability: AiCapability, providerId: String?) {
        _binding.value = _binding.value.bind(capability, providerId)
        persistBinding(_binding.value)
    }

    private fun persistProviders(list: List<AiProviderConfig>) {
        val array = JSONArray()
        list.forEach { config ->
            array.put(
                JSONObject().apply {
                    put("id", config.id)
                    put("name", config.name)
                    put("baseUrl", config.baseUrl)
                    put("apiKey", config.apiKey)
                    put("textModel", config.textModel)
                    put("visionModel", config.visionModel)
                    put("isBuiltIn", config.isBuiltIn)
                    put("consoleUrl", config.consoleUrl)
                    put("hint", config.hint)
                }
            )
        }
        prefs.edit { putString(KEY_PROVIDERS, array.toString()) }
    }

    private fun persistBinding(binding: AiBinding) {
        prefs.edit {
            putString(
                KEY_BINDING,
                JSONObject().apply {
                    put("text", binding.textProviderId.orEmpty())
                    put("vision", binding.visionProviderId.orEmpty())
                }.toString(),
            )
        }
    }

    // ---------------------------------------------------------------- 取用

    fun provider(id: String?): AiProviderConfig? =
        id?.let { target -> _providers.value.firstOrNull { it.id == target } }

    /** 取当前绑定且**真的可用**的供应商；没绑定就按顺序找第一个能用的 */
    fun resolve(capability: AiCapability): AiProviderConfig? {
        provider(_binding.value.providerIdFor(capability))?.takeIf { it.usableFor(capability) }
            ?.let { return it }
        return _providers.value.firstOrNull { it.usableFor(capability) }
    }

    fun isReady(capability: AiCapability): Boolean = resolve(capability) != null

    companion object {
        private const val KEY_PROVIDERS = "ai_providers_v1"
        private const val KEY_BINDING = "ai_binding_v1"
    }
}
