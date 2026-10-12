package com.calorietracker.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 大模型返回 JSON 的容错解析。
 *
 * 现实情况是：即使明确要求「只返回 JSON 数组」，模型仍会加 markdown 代码块、
 * 前面**先夸你两句**、末尾多一个逗号。这里负责把这些都吃掉，
 * 而不是让一次格式抖动直接变成「识别失败」。
 */
object AiJson {

    /** 从自由文本里抠出 JSON 数组；找不到返回空列表 */
    fun extractArray(text: String): List<JSONObject> {
        val cleaned = stripFence(text)
        balancedBlocks(cleaned, '[', ']').forEach { block ->
            val fixed = removeTrailingCommas(block)
            runCatching {
                val array = JSONArray(fixed)
                return (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)
                }
            }
        }
        // 退化：单个对象也接受，当作只有一项
        balancedBlocks(cleaned, '{', '}').forEach { block ->
            val fixed = removeTrailingCommas(block)
            runCatching { return listOf(JSONObject(fixed)) }
        }
        return emptyList()
    }

    /** 去掉 ```json ... ``` 包裹 */
    private fun stripFence(text: String): String {
        val fence = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```")
        val match = fence.find(text)
        return if (match != null) match.groupValues[1] else text
    }

    /** 找出所有「括号配对完整」的片段，字符串里的括号不参与配对 */
    private fun balancedBlocks(text: String, open: Char, close: Char): List<String> {
        val blocks = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false

        text.forEachIndexed { index, char ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            when {
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == open -> {
                    if (depth == 0) start = index
                    depth++
                }
                !inString && char == close -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        blocks += text.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return blocks
    }

    /** 去掉对象/数组里的尾逗号（字符串内部的逗号不动） */
    private fun removeTrailingCommas(text: String): String {
        val builder = StringBuilder(text.length)
        var inString = false
        var escaped = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (escaped) {
                builder.append(char)
                escaped = false
                index++
                continue
            }
            when {
                char == '\\' && inString -> {
                    builder.append(char)
                    escaped = true
                }
                char == '"' -> {
                    inString = !inString
                    builder.append(char)
                }
                !inString && char == ',' -> {
                    // 看后面第一个非空白字符是不是 } 或 ]
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && (text[next] == '}' || text[next] == ']')) {
                        // 丢掉这个逗号
                    } else {
                        builder.append(char)
                    }
                }
                else -> builder.append(char)
            }
            index++
        }
        return builder.toString()
    }

    /**
     * 数值兼容：模型可能给 "12" / "12.5" / "约 200 克" / null。
     * 解析不出来返回 null —— 让调用方决定怎么兜底，而不是这里悄悄当 0。
     */
    fun optDouble(json: JSONObject, key: String): Double? {
        if (json.isNull(key)) return null
        json.opt(key)?.let { value ->
            when (value) {
                is Number -> return value.toDouble()
                is String -> {
                    val match = Regex("-?\\d+(?:\\.\\d+)?").find(value) ?: return null
                    return match.value.toDoubleOrNull()
                }
            }
        }
        return null
    }

    fun optInt(json: JSONObject, key: String): Int? = optDouble(json, key)?.let { Math.round(it).toInt() }

    /**
     * 数值兼容 + **多字段名容错**：模型可能用不同键名回答同一个值。
     *
     * 名字（`name`/`foodName`/`食物`）早就做了容错，营养值却一直只认死一个键名 ——
     * 模型写成 `kcal_per_100g` 就会被当成「没给」，那条食物直接掉到 0 千卡。
     * 键名表见 [AiValueRule] 里的 `*_PER_100G_KEYS`。
     */
    fun optDoubleAny(json: JSONObject, keys: List<String>): Double? {
        keys.forEach { key ->
            optDouble(json, key)?.let { return it }
        }
        return null
    }

    fun optString(json: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            val value = json.optString(key).trim()
            if (value.isNotEmpty() && value != "null") return value
        }
        return null
    }
}
