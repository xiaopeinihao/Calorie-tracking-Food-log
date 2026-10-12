package com.calorietracker.app.ui.navigation

/** 全部路由。4 个 tab + 3 个全屏流程页。 */
object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val STATS = "stats"
    const val PROFILE = "profile"

    const val CAMERA = "camera"
    const val CONFIRM = "confirm"
    const val MANUAL = "manual"

    /** AI 配置 */
    const val AI_SETTINGS = "ai_settings"

    /** 一句话记一餐（语音由输入法负责，App 不自己接） */
    const val QUICK_INPUT = "quick_input"

    /** 底部导航 4 个 tab，顺序即展示顺序 */
    val tabs = listOf(HOME, RECORD, STATS, PROFILE)
}
