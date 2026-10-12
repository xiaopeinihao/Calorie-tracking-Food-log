package com.calorietracker.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 圆角规范：卡片 24dp / 按钮 28dp / 胶囊 20dp / 输入框 16dp
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val LightColors = lightColorScheme(
    primary = CalorieColors.Primary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CalorieColors.PrimaryContainer,
    onPrimaryContainer = CalorieColors.TextPrimary,
    secondary = CalorieColors.Accent,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = CalorieColors.Fat,
    background = CalorieColors.Background,
    onBackground = CalorieColors.TextPrimary,
    surface = CalorieColors.Surface,
    onSurface = CalorieColors.TextPrimary,
    surfaceVariant = CalorieColors.PrimarySoft,
    onSurfaceVariant = CalorieColors.TextSecondary,
    outline = CalorieColors.Divider,
    outlineVariant = CalorieColors.Divider,
)

/**
 * 该产品刻意只提供浅色主题：设计稿以大量留白 + 浅底分层，
 * 强行深色化会破坏「不制造焦虑」的视觉基调。
 */
@Composable
fun CalorieTrackerTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // 保留调用以便日后扩展，当前不改变配色

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    LocalContext.current

    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
