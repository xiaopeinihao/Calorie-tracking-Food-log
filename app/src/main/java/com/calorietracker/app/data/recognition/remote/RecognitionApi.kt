package com.calorietracker.app.data.recognition.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 真实识别服务的 Retrofit 空壳。
 *
 * ⚠️ 本工程**没有接任何第三方服务**：`BuildConfig.RECOGNITION_API_KEY` 为空时，
 * 依赖注入会自动回落到 [com.calorictracker.app.data.recognition.MockRecognitionService]。
 * 这里只把请求/响应契约固定下来，方便二期接入时不动 UI 层。
 */
interface RecognitionApi {

    @Multipart
    @POST("v1/food/recognize")
    suspend fun recognize(
        @Part image: MultipartBody.Part,
    ): RecognitionResponse
}

@JsonClass(generateAdapter = false)
data class RecognitionResponse(
    @Json(name = "items") val items: List<RecognizedDto> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class RecognizedDto(
    @Json(name = "name") val name: String,
    @Json(name = "grams") val grams: Int,
    @Json(name = "confidence") val confidence: Float,
)
