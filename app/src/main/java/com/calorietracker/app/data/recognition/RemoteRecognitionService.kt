package com.calorietracker.app.data.recognition

import android.content.Context
import android.net.Uri
import com.calorietracker.app.data.recognition.remote.RecognitionApi
import com.calorietracker.app.domain.model.RecognizedItem
import com.calorietracker.app.domain.recognition.FoodRecognitionService
import com.calorietracker.app.domain.recognition.RecognitionException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真实识别服务实现（当前为骨架，没有接任何第三方服务）。
 *
 * 只有在 `BuildConfig.RECOGNITION_API_KEY` 非空时才会被注入，见
 * [com.calorietracker.app.di.RecognitionModule]。
 */
@Singleton
class RemoteRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RecognitionApi,
) : FoodRecognitionService {

    override suspend fun recognize(imageUri: Uri): List<RecognizedItem> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw RecognitionException.ServiceError("无法读取照片")

        val part = MultipartBody.Part.createFormData(
            name = "image",
            filename = "meal.jpg",
            body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()),
        )

        try {
            withTimeout(TIMEOUT_MS) {
                val response = api.recognize(part)
                response.items
                    .filter { it.name.isNotBlank() && it.grams > 0 }
                    .map {
                        RecognizedItem(
                            name = it.name,
                            estimatedGrams = it.grams,
                            confidence = it.confidence,
                        )
                    }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw RecognitionException.Timeout()
        } catch (e: RecognitionException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 用户离开页面导致的取消：原样抛出，不要包装成「识别服务异常」——
            // 那会把协程取消变成一个假的业务错误。
            throw e
        } catch (e: Exception) {
            throw RecognitionException.ServiceError(e.message ?: "识别服务异常")
        }
    }

    companion object {
        /** 识别超时上限：10 秒（产品要求，超过就给「重试 / 手动录入」出口） */
        const val TIMEOUT_MS = 10_000L
    }
}
