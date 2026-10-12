package com.calorietracker.app.domain.model

/**
 * 识别服务返回的单条结果。只携带名称、估算克数与置信度；
 * 具体热量由 UI 层用本地食物库 [com.calorietracker.app.data.local.entity.FoodItem] 换算，
 * 从而保证热量数值始终来自可核对的营养库，而不是识别服务临时编造。
 */
data class RecognizedItem(
    val name: String,
    val estimatedGrams: Int,
    val confidence: Float,
)
