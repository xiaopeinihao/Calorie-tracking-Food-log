package com.calorietracker.app.data.ai

/**
 * AI 能力。一个供应商里可以同时配多个模型，**能力是否可用 = 对应模型名是否非空**——
 * 这是从 BeeCount 借鉴的核心结构：换识别渠道只需要切绑定，不用改识别代码。
 *
 * 只有两种能力：图片（拍照识别）与文本（一句话记账）。
 * **不做语音**：输入法自带语音转文字，App 再塞一个只会多占权限、多引一个模型配置。
 */
enum class AiCapability(val label: String) {
    TEXT("文本"),
    VISION("图片"),
}

/**
 * 一个 AI 服务商配置。
 *
 * @param baseUrl OpenAI 兼容的根地址，末尾不带 `/`，例如 `https://api.deepseek.com/v1`
 * @param apiKey  明文存本地（与 BeeCount 一致）。App 不联网透传，只在你点识别时发给该服务商。
 */
data class AiProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    /** 内置预设不可删除，可以改 */
    val isBuiltIn: Boolean = false,
    /** 预设的申请入口，方便用户在配置页直接跳去拿 Key */
    val consoleUrl: String = "",
    val hint: String = "",
) {
    val supportsText: Boolean get() = textModel.isNotBlank()
    val supportsVision: Boolean get() = visionModel.isNotBlank()

    /** 能不能真的用：有 Key、有地址、对应模型非空 */
    fun usableFor(capability: AiCapability): Boolean {
        if (apiKey.isBlank() || baseUrl.isBlank()) return false
        return when (capability) {
            AiCapability.TEXT -> supportsText
            AiCapability.VISION -> supportsVision
        }
    }

    fun modelFor(capability: AiCapability): String = when (capability) {
        AiCapability.TEXT -> textModel
        AiCapability.VISION -> visionModel
    }
}

/** 能力绑定：文本 / 图片 各用哪个供应商 */
data class AiBinding(
    val textProviderId: String? = null,
    val visionProviderId: String? = null,
) {
    fun providerIdFor(capability: AiCapability): String? = when (capability) {
        AiCapability.TEXT -> textProviderId
        AiCapability.VISION -> visionProviderId
    }

    fun bind(capability: AiCapability, providerId: String?): AiBinding = when (capability) {
        AiCapability.TEXT -> copy(textProviderId = providerId)
        AiCapability.VISION -> copy(visionProviderId = providerId)
    }
}

/**
 * 常见服务商预设。地址与模型名都是各家官方文档里的 OpenAI 兼容入口。
 * 只填「能确定」的东西，拿不准的留空让用户自己填。
 */
object AiPresets {

    fun defaults(): List<AiProviderConfig> = listOf(
        AiProviderConfig(
            id = "tokenhub",
            name = "腾讯 TokenHub",
            baseUrl = "https://tokenhub.tencentmaas.com/v1",
            // 文本用 hy4-preview（深度思考），视觉用 deepseek/deepseek-flash。
            // 这正是「一个服务商可分别配三种模型」的用处：各取所长。
            // 实测（同一句话 / 同一张番茄炒蛋照片，2026-10-08）：
            //   hy4-preview            文本 6.3s
            //   deepseek/deepseek-flash 文本 1.3s  视觉 2.9s（答「番茄炒蛋」）
            //   glm-5.3-flash          文本 3.0s  视觉 3.5s（答「番茄炒蛋」）
            // 嫌一句话解析慢的话，把文本模型换成 deepseek/deepseek-flash 即可。
            textModel = "hy4-preview",
            visionModel = "deepseek/deepseek-flash",
            isBuiltIn = true,
            consoleUrl = "https://tokenhub.tencentmaas.com",
            hint = "文本 hy4-preview（深度思考，稍慢但更稳）；视觉 deepseek/deepseek-flash。也可换 glm-5.3-flash / kimi-k3。",
        ),
        AiProviderConfig(
            id = "zhipu",
            name = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            textModel = "glm-4-flash",
            visionModel = "glm-4v-flash",
            isBuiltIn = true,
            consoleUrl = "https://open.bigmodel.cn/usercenter/apikeys",
            hint = "免费额度较多，glm-4v-flash 支持图片识别，适合拍照记账",
        ),
        AiProviderConfig(
            id = "dashscope",
            name = "通义千问（阿里云百炼）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            textModel = "qwen-plus",
            visionModel = "qwen-vl-max",
            isBuiltIn = true,
            consoleUrl = "https://bailian.console.aliyun.com/?apiKey=1",
            hint = "qwen-vl-max 对中餐识别效果较好",
        ),
        AiProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            textModel = "deepseek-chat",
            // DeepSeek 目前没有视觉模型，图片识别请绑定别的服务商
            visionModel = "",
            isBuiltIn = true,
            consoleUrl = "https://platform.deepseek.com/api_keys",
            hint = "文本模型便宜好用；没有视觉模型，拍照识别请另绑一家",
        ),
        AiProviderConfig(
            id = "siliconflow",
            name = "硅基流动 SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1",
            textModel = "Qwen/Qwen2.5-7B-Instruct",
            visionModel = "Qwen/Qwen2.5-VL-32B-Instruct",
            isBuiltIn = true,
            consoleUrl = "https://cloud.siliconflow.cn/account/ak",
            hint = "一个 Key 里文本与图片模型都齐，国内直连快",
        ),
        AiProviderConfig(
            id = "moonshot",
            name = "Moonshot Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            textModel = "moonshot-v1-8k",
            visionModel = "moonshot-v1-8k-vision-preview",
            isBuiltIn = true,
            consoleUrl = "https://platform.moonshot.cn/console/api-keys",
        ),
        AiProviderConfig(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            textModel = "gpt-4o-mini",
            visionModel = "gpt-4o-mini",
            isBuiltIn = true,
            consoleUrl = "https://platform.openai.com/api-keys",
            hint = "国内访问需要自备网络环境",
        ),
        AiProviderConfig(
            id = "custom",
            name = "自定义（OpenAI 兼容）",
            baseUrl = "",
            isBuiltIn = true,
            consoleUrl = "",
            hint = "任何兼容 /chat/completions 的服务：Ollama、one-api、vLLM 等",
        ),
    )
}
