package com.calorietracker.app.data.ai

/**
 * AI 调用失败分类。分类的意义是给用户**能照着做**的提示：
 * 「去填 Key」「等一会儿」「换模型」「检查网络」，而不是甩一个堆栈。
 */
class AiException(
    val kind: Kind,
    override val message: String,
    val cause2: Throwable? = null,
) : Exception(message, cause2) {

    enum class Kind {
        /** 还没配置能用的服务商 */
        NOT_CONFIGURED,

        /** Key 无效 / 无权限 */
        UNAUTHORIZED,

        /** 触发限流 */
        RATE_LIMITED,

        /** 请求被服务商拒绝（参数不兼容等） */
        BAD_REQUEST,

        /** 模型名或地址不存在 */
        NOT_FOUND,

        /** 超时 */
        TIMEOUT,

        /** 网络不通 */
        NETWORK,

        /** 服务端错误 */
        SERVER,

        /** 返回内容为空或无法解析 */
        EMPTY_RESPONSE,
    }

    /**
     * 给用户看的、可执行的提示。
     *
     * 注意：这里**不拼接 [message]**（除了 EMPTY_RESPONSE），因为 message 里带的是
     * 服务商返回的原始 body —— 常常是一大坨英文 JSON。把它甩到界面上，
     * 用户得到的信息量为零，还以为是 App 崩了。原始内容仍然完整保留在 message 里供日志排查。
     */
    val userMessage: String
        get() = when (kind) {
            Kind.NOT_CONFIGURED -> "还没有配置 AI：去「我的 → AI 设置」填一个服务商的 API Key"
            Kind.UNAUTHORIZED -> "API Key 无效或没有权限，去 AI 设置里检查一下"
            Kind.RATE_LIMITED -> "请求太频繁或额度用完了，稍后再试"
            Kind.BAD_REQUEST -> "服务商拒绝了这次请求（多半是模型名或参数不兼容），去 AI 设置换个模型试试"
            Kind.NOT_FOUND -> "模型名或接口地址不对，去 AI 设置里核对"
            Kind.TIMEOUT -> "AI 响应超时了，可以重试或换个更快的模型"
            Kind.NETWORK -> "网络不通，检查一下连接"
            Kind.SERVER -> "AI 服务商出错了，稍后再试"
            // EMPTY_RESPONSE 的 message 是**我们自己**写的短句（空内容 / 不是 JSON / 没识别到食物），
            // 不是服务商原文，所以这里可以直接带出来。
            Kind.EMPTY_RESPONSE -> "AI 没返回可用结果：$message"
        }

    companion object {
        fun notConfigured() = AiException(Kind.NOT_CONFIGURED, "未配置 AI 服务商")

        fun fromHttp(code: Int, body: String): AiException {
            val snippet = body.take(300).ifBlank { "HTTP $code" }
            return when (code) {
                401, 403 -> AiException(Kind.UNAUTHORIZED, "HTTP $code：$snippet")
                429 -> AiException(Kind.RATE_LIMITED, "HTTP 429：$snippet")
                404 -> AiException(Kind.NOT_FOUND, "HTTP 404：$snippet")
                in 400..499 -> AiException(Kind.BAD_REQUEST, "HTTP $code：$snippet")
                else -> AiException(Kind.SERVER, "HTTP $code：$snippet")
            }
        }
    }
}
