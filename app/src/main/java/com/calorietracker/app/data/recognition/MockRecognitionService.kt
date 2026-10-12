package com.calorietracker.app.data.recognition

import android.net.Uri
import com.calorietracker.app.data.local.dao.FoodDao
import com.calorietracker.app.domain.model.RecognizedItem
import com.calorietracker.app.domain.recognition.FoodRecognitionService
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 离线模拟识别：延迟 1.5 秒后，从**本地权威食物库**里随机抽 2–4 条返回。
 *
 * 这样做的好处是：整条拍照链路可以先跑通、可演示、可截图，
 * 而结果页显示的热量仍然是真的（来自食物库），不会出现演示时数字离谱的情况。
 */
@Singleton
class MockRecognitionService @Inject constructor(
    private val foodDao: FoodDao,
) : FoodRecognitionService {

    /** 可注入替换，方便单测里固定随机种子 */
    internal var random: Random = Random.Default

    override suspend fun recognize(imageUri: Uri): List<RecognizedItem> {
        delay(SIMULATED_LATENCY_MS)
        val count = random.nextInt(2, 5) // 2..4
        val foods = foodDao.randomPick(count)
        return foods.map { food ->
            RecognizedItem(
                name = food.name,
                // 用该食物的「常见分量」做基准，上下浮动 ±20%，模拟识别出的估算分量
                estimatedGrams = jitter(food.commonServingGrams),
                confidence = MIN_CONFIDENCE + random.nextFloat() * (MAX_CONFIDENCE - MIN_CONFIDENCE),
            )
        }
    }

    private fun jitter(base: Int): Int {
        if (base <= 0) return 100
        val factor = 0.8 + random.nextDouble() * 0.4
        return (base * factor).toInt().coerceAtLeast(10)
    }

    companion object {
        const val SIMULATED_LATENCY_MS = 1_500L
        const val MIN_CONFIDENCE = 0.6f
        const val MAX_CONFIDENCE = 0.95f
    }
}
