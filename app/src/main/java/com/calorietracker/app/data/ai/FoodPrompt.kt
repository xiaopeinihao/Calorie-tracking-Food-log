package com.calorietracker.app.data.ai

/**
 * 识别 prompt。刻意采用「把全部指令、上下文、输出格式拼进一条 user 消息」的写法
 * （BeeCount 的做法）：不依赖服务商对 system role 的支持，也不会因为多轮消息被截断。
 *
 * 三条硬规则写在 prompt 里，同时也由本地代码兜底校验，不是只靠模型自觉：
 *  1. 份量必须有数字；
 *  2. **热量必须给一个数字**（本身说不清这道菜就给估算值）。以前这里写的是
 *     「不确定就填 null」，于是库里查不到的复合菜（青椒炒肉、小炒肉…）真的全填 null，
 *     结果页只能把它显示成 0 千卡 —— 用户看到的就是「AI 连热量都不给」；
 *  3. **碳水 / 蛋白 / 脂肪不确定时填 null** —— 这三项宁可缺也不能凭感觉编，
 *     缺项由本地标成「仅热量」，绝不把缺失当成 0。
 */
object FoodPrompt {

    fun build(
        now: String,
        mealHint: String,
        candidates: List<String> = emptyList(),
        userText: String? = null,
    ): String = buildString {
        appendLine("你是中文食物营养识别助手。请根据用户提供的${if (userText == null) "食物照片" else "文字描述"}，识别出吃了什么，并给出每 100 克的营养值。")
        appendLine()
        appendLine("当前时间：$now（据此判断餐次，当前大概属于：$mealHint）")
        if (candidates.isNotEmpty()) {
            appendLine("本地食物库里已有这些相关条目，**如果你要描述的食物与其中之一是同一种，name 必须原样使用该条目名**：")
            candidates.forEach { appendLine("  - $it") }
        }
        if (userText != null) {
            appendLine()
            appendLine("用户描述：")
            appendLine(userText)
        }
        appendLine()
        appendLine("输出要求（违反任何一条都视为失败）：")
        appendLine("1. 只输出一个 JSON 数组，不要解释、不要 markdown 代码块、不要前后缀文字。")
        appendLine("2. 数组元素字段：")
        appendLine("""   {"name":"食物名（中文，用家常叫法）","grams":份量克数(整数),""")
        appendLine("""    "kcalPer100g":每100克热量千卡(数字，必填),"carbsPer100g":碳水克数(数字或null),""")
        appendLine("""    "proteinPer100g":蛋白质克数(数字或null),"fatPer100g":脂肪克数(数字或null),"confidence":0到1}""")
        appendLine("3. grams 必须是数字：按常见份量估算（一碗米饭约200g、一个鸡蛋约50g、一份炒菜约250g、一杯牛奶约250g、一个苹果约180g）。")
        appendLine("4. **kcalPer100g 必须给一个数字**（千卡/100g）。优先用你知道的标准值；")
        appendLine("   如果说不清这道菜，就按同类菜的常见做法估算一个最接近的值（例如「青椒炒肉」参考小炒肉/回锅肉这一档）。")
        appendLine("   **不要填 null** —— 填 null 的结果是这一项只能显示「待补充」、等于没给出热量，这不是我们要的。")
        appendLine("5. **碳水 / 蛋白 / 脂肪（每 100g）只要不确定就必须填 null**，绝不要凭感觉编一个数。")
        appendLine("   缺哪项就填哪项 null，本地会标注「仅热量」，不会把缺失当成 0。")
        appendLine("6. 中餐按「主食 / 菜 / 汤 / 饮料」拆成独立条目；一道菜不要拆成原料（不要把番茄炒蛋拆成番茄和鸡蛋）。")
        appendLine("7. 油、糖、酱汁这类调料只有明显改变热量时才单独列出（例如「红烧肉」不用再单列油）。")
        appendLine("8. 最多 8 条，按份量从大到小排序。")
        appendLine("9. 如果内容不是食物（例如拍到人、桌面、风景），返回 []。")
        appendLine("10. 只输出 JSON 数组本身，第一个字符必须是 [，最后一个字符必须是 ]。")
    }

    /**
     * **第二轮追问**：只问「这些食物每 100 克的营养值」。
     *
     * 为什么需要它：第一轮 prompt 为了拦编造写的是「只要有不确定就填 null，绝对不要凑数编数字」，
     * 模型对库里查不到的复合菜经常真的填 null，于是结果页那几项变成 **0 千卡**。
     * 与其显示 0，不如明确告诉它「这一轮允许给估算值」——拿到的值仍然走同一道
     * 区间 + Atwater 校验，出处也仍然标成「AI 估算（非成分表）」，与权威数据分开显示。
     *
     * 字段名在这一轮里写死，免得模型换个键名回答又解析不出来。
     */
    fun buildNutritionLookup(names: List<String>): String = buildString {
        appendLine("你是中文食物营养助手。请给出下面这些食物**每 100 克**的能量与三大营养素。")
        appendLine()
        names.forEach { appendLine("- $it") }
        appendLine()
        appendLine("输出要求（违反任何一条都视为失败）：")
        appendLine("1. 只输出一个 JSON 数组，不要解释、不要 markdown 代码块、不要前后缀文字。")
        appendLine("2. 每个元素的字段名必须**完全**是这几个（不要改名、不要加后缀）：")
        appendLine("""   {"name":"食物名","kcalPer100g":数字,"carbsPer100g":数字,"proteinPer100g":数字,"fatPer100g":数字}""")
        appendLine("3. name 必须**原样**使用上面给出的名字。")
        appendLine("4. 这是「每 100 克」的值，不是一份的值。**这一轮允许给估算值**：")
        appendLine("   请在你确实知道的范围内给出最接近的数字，**不要因为不确定就填 null**——")
        appendLine("   这一轮的目的就是补上第一轮缺掉的值。")
        appendLine("5. 只有某个值你完全无法估计时才填 null。")
        appendLine("6. 第一个字符必须是 [，最后一个字符必须是 ]。")
    }

    /** 从用户文字里挑几个关键词，去本地库找候选条目名，喂给模型让它复用权威条目名 */
    fun keywords(text: String): List<String> {
        val cleaned = text.replace(Regex("[，。！？、,.!?；;：:\\s]+"), " ")
        return cleaned.split(" ")
            .map { it.trim() }
            .filter { it.length in 2..6 }
            .distinct()
            .take(6)
    }
}
