package com.calorietracker.app.data.ai

import android.util.Base64
import com.calorietracker.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 一次能力测试的结果 */
data class AiTestResult(
    val capability: AiCapability,
    val ok: Boolean,
    val message: String,
)

/**
 * OpenAI 兼容的大模型客户端。
 *
 * 所有能用的国内服务商（智谱、通义、硅基流动、Moonshot、DeepSeek）以及 one-api / Ollama
 * 都提供 `/chat/completions`，所以**只写一个客户端**就够了，换服务商只改配置。
 *
 * 借鉴 BeeCount 的两处兼容处理：
 *  1. 图片一律用 `data:image/jpeg;base64,` 内联（不依赖服务商能访问外链）；
 *  2. **参数摘除重试**：有些模型（推理类）会拒绝 `temperature`，4xx 时从我们自己发出的参数里
 *     找出被点名的那一个摘掉重发，最多 3 次，避免「换个模型就报错」。
 */
@Singleton
class AiClient @Inject constructor() {

    /**
     * 超时策略：**宁可明确失败，也不让「正在识别…」无限转下去。**
     *
     * 之前 readTimeout 给了 180 秒且没有整通上限，服务商排队/卡死时用户最长要干等
     * 3 分钟才看到报错——这是「拍照识别要反应很久」最主要的来源。
     */
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 整通请求的硬上限：包含连接、上传、等待、下载，也包含 OkHttp 内部重发与参数摘除重试，
        // 所以这就是用户实际最多会等的时间。
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 文本对话 */
    suspend fun chat(
        config: AiProviderConfig,
        prompt: String,
        capability: AiCapability = AiCapability.TEXT,
        imageJpeg: ByteArray? = null,
    ): String {
        require(config.baseUrl.isNotBlank()) { "baseUrl 为空" }
        val model = config.modelFor(capability)
        if (model.isBlank()) throw AiException.notConfigured()

        // 一张 300KB 的 JPEG base64 之后接近 400KB 字符串，再加上 JSON 转义：
        // 这活儿不能压在主线程上做（会掉帧），放到 Default 里拼。
        val messages = withContext(Dispatchers.Default) {
            val content = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                if (imageJpeg != null) {
                    val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(imageJpeg, Base64.NO_WRAP)
                    put(
                        JSONObject().put("type", "image_url")
                            .put("image_url", JSONObject().put("url", dataUrl))
                    )
                }
            }
            JSONArray().put(
                JSONObject().put("role", "user").put("content", content)
            )
        }

        return complete(config, model, messages)
    }

    /** 试一下某个模型能不能用：发一句 "hi"（图片模型发一张 1x1 的图） */
    suspend fun test(config: AiProviderConfig, capability: AiCapability): AiTestResult {
        if (config.apiKey.isBlank()) {
            return AiTestResult(capability, false, "还没填 API Key")
        }
        if (config.baseUrl.isBlank()) {
            return AiTestResult(capability, false, "还没填接口地址")
        }
        if (config.modelFor(capability).isBlank()) {
            return AiTestResult(capability, false, "还没填${capability.label}模型名")
        }
        return try {
            when (capability) {
                AiCapability.VISION -> chat(config, "这张图是什么颜色？只回答颜色。", capability, TINY_JPEG)
                AiCapability.TEXT -> chat(config, "回复 ok", capability)
            }
            AiTestResult(capability, true, "连接正常")
        } catch (e: AiException) {
            AiTestResult(capability, false, e.userMessage)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 测试被取消（用户退出设置页）不算失败结果，原样抛出
            throw e
        } catch (e: Exception) {
            // 不要把 e.message 直接给用户看：那多是英文异常串（SocketTimeoutException、
            // JSONException…），甚至是服务端返回的原始 JSON。细节留在日志里。
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AiClient", "test 失败：${e.javaClass.simpleName} ${e.message}")
            }
            AiTestResult(capability, false, "连接失败，检查接口地址与网络")
        }
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun complete(
        config: AiProviderConfig,
        model: String,
        messages: JSONArray,
    ): String {
        val url = "${normalizeBase(config.baseUrl)}/chat/completions"
        // 可以被「参数摘除重试」动态拿掉的参数。
        // max_tokens 给到 4096：hy4-preview 这类**深度思考**模型会先烧掉一大段推理 token，
        // 给 2048 时实测出现过 reasoning 用满、content 为空的情况。
        val optional = linkedMapOf<String, Any>(
            "temperature" to 0.2,
            "max_tokens" to 4096,
        )
        var lastError: AiException? = null

        repeat(MAX_PARAM_STRIPS + 1) { attempt ->
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("stream", false)
                optional.forEach { (key, value) -> put(key, value) }
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON))
                .build()

            try {
                return execute(request) { body -> readContent(body) }
            } catch (e: AiException) {
                lastError = e
                val stripped = stripRejectedParam(e, optional)
                if (!stripped || attempt == MAX_PARAM_STRIPS) throw e
                // 关键：只有真的从「我们发出去的参数」里摘掉了东西才重试
            }
        }
        throw lastError ?: AiException(AiException.Kind.SERVER, "调用失败")
    }

    /** 从错误信息里找出被拒绝的可选参数，摘掉后重试 */
    private fun stripRejectedParam(error: AiException, optional: MutableMap<String, Any>): Boolean {
        if (error.kind != AiException.Kind.BAD_REQUEST) return false
        val mentioned = optional.keys.firstOrNull { key ->
            error.message.contains(key, ignoreCase = true)
        } ?: return false
        optional.remove(mentioned)
        return true
    }

    /**
     * 发请求并解析响应。
     *
     * 用 `enqueue` + [suspendCancellableCoroutine]，**不用阻塞式的 `execute()`**：
     * 阻塞调用对协程取消是免疫的，于是 `withTimeoutOrNull` 这类超时根本不生效
     * （实测：第二轮追问设了 15 秒上限，40 秒后仍在跑，等于上限形同虚设）。
     * 注册 `invokeOnCancellation { call.cancel() }` 之后取消能真正掐断底层请求：
     * 既让超时可用，也让用户离开页面时不再白占一条连接。
     */
    private suspend fun <T> execute(request: Request, parse: (String) -> T): T =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (!continuation.isActive) return
                    continuation.resumeWith(Result.failure(mapIOException(e)))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    continuation.resumeWith(
                        runCatching {
                            response.use { res ->
                                val body = res.body?.string().orEmpty()
                                if (!res.isSuccessful) throw AiException.fromHttp(res.code, body)
                                parse(body)
                            }
                        },
                    )
                }
            })
        }

    /**
     * IOException → 面向用户的分类。
     *
     * 读超时（`SocketTimeoutException`）与 `callTimeout` 抛出的 `InterruptedIOException`
     * 都要归成「超时」，否则会被当成「网络不通」，提示就把用户带偏了。
     */
    private fun mapIOException(e: IOException): AiException = when (e) {
        is InterruptedIOException -> AiException(AiException.Kind.TIMEOUT, "请求超时", e)
        else -> AiException(AiException.Kind.NETWORK, e.message ?: "网络错误", e)
    }

    /** choices[0].message.content，content 可能是字符串也可能是分段数组 */
    private fun readContent(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrElse {
            // 原文只进日志，不进用户界面：那多半是一大坨英文/HTML 错误页
            if (BuildConfig.DEBUG) android.util.Log.d("AiClient", "返回不是 JSON：${body.take(400)}")
            throw AiException(AiException.Kind.EMPTY_RESPONSE, "服务商返回的不是 JSON，检查接口地址是否指向 /chat/completions")
        }
        json.optJSONObject("error")?.let { error ->
            throw AiException(
                AiException.Kind.BAD_REQUEST,
                error.optString("message").ifBlank { "服务商返回错误" },
            )
        }
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw AiException(AiException.Kind.EMPTY_RESPONSE, "返回里没有 choices")
        }
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw AiException(AiException.Kind.EMPTY_RESPONSE, "返回里没有 message")
        val content = message.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val part = content.optJSONObject(index)
                    append(part?.optString("text").orEmpty())
                }
            }
            else -> ""
        }
        if (text.isNotBlank()) return text

        // 思考型模型（hy4-preview / deepseek 系列）偶尔把结果只放在 reasoning_content 里，
        // content 留空。直接判失败会让用户莫名其妙，这里退一步去推理内容里找 JSON。
        val reasoning = message.optString("reasoning_content")
        if (reasoning.isNotBlank() && AiJson.extractArray(reasoning).isNotEmpty()) {
            return reasoning
        }
        throw AiException(
            AiException.Kind.EMPTY_RESPONSE,
            "模型返回了空内容（finish_reason=${choices.optJSONObject(0)?.optString("finish_reason")}，" +
                "推理长度=${reasoning.length}），换个模型或重试一次",
        )
    }

    private fun normalizeBase(baseUrl: String): String = baseUrl.trim().trimEnd('/')


    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** 连不上就早点说，别耗着 */
        const val CONNECT_TIMEOUT_SECONDS = 10L

        /** 单次读超时：视觉模型正常 3~15 秒出结果，60 秒已经非常宽容 */
        const val READ_TIMEOUT_SECONDS = 60L
        const val WRITE_TIMEOUT_SECONDS = 60L

        /** 整通请求硬上限（含重发）：这是用户实际最多会等待的时间 */
        const val CALL_TIMEOUT_SECONDS = 75L

        const val MAX_PARAM_STRIPS = 3

        /** 1x1 白色 JPEG，用于测试图片模型连通性 */
        val TINY_JPEG: ByteArray = Base64.decode(
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a" +
                "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA" +
                "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==",
            Base64.DEFAULT,
        )
    }
}
