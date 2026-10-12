package com.calorietracker.app.domain.recognition

import android.net.Uri
import com.calorietracker.app.domain.model.RecognizedItem

/**
 * 识别能力抽象。UI 只依赖这个接口，不依赖任何具体实现，
 * 因此「Mock（离线）」与「Remote（真实模型）」可以随时互换，见 README「如何切换识别服务」。
 *
 * 约定：实现**只允许**返回食物名称、估算克数与置信度；
 * 热量与营养素必须由 UI/仓库层用本地权威食物库换算，
 * 避免识别端返回一个无法核对的热量数字。
 */
interface FoodRecognitionService {

    /** 识别失败（含超时）时抛 [RecognitionException]，调用方负责给出「重试 / 手动录入」出口。 */
    suspend fun recognize(imageUri: Uri): List<RecognizedItem>
}

/** 识别失败原因，用于结果页与失败态的文案 */
sealed class RecognitionException(message: String) : Exception(message) {
    /** 超过 10 秒仍未返回 */
    class Timeout : RecognitionException("识别超时")
    /** 没有网络 */
    class Offline : RecognitionException("网络不可用")
    /** 服务返回异常 */
    class ServiceError(message: String) : RecognitionException(message)
    /** 没有识别到任何食物（不是错误，但需要单独给出口） */
    class EmptyResult : RecognitionException("没有识别到食物")
}
