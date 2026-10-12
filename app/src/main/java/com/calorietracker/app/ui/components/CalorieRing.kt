package com.calorietracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calorietracker.app.ui.theme.BigNumberStyle
import com.calorietracker.app.ui.theme.CalorieColors

/**
 * 首页核心圆环：手写 Canvas，不引第三方图表库。
 * 从 12 点方向顺时针增长，超过 100% 时进度停在满圈但数字照实显示。
 *
 * @param intake 已摄入千卡
 * @param target 目标千卡
 */
@Composable
fun CalorieRing(
    intake: Double,
    target: Double,
    modifier: Modifier = Modifier,
    ringSize: androidx.compose.ui.unit.Dp = 240.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 14.dp,
    label: String = "已摄入 · 目标 %d",
    content: @Composable () -> Unit = {},
) {
    val progress = if (target > 0) (intake / target).coerceIn(0.0, 1.0).toFloat() else 0f
    val hasData = intake > 0.0

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // 轨道
            drawArc(
                color = CalorieColors.RingTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (progress > 0f) {
                // 主色渐变，让圆环有一点「呼吸感」
                val brush = Brush.linearGradient(
                    colors = listOf(CalorieColors.Primary, CalorieColors.Fat),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                )
                drawArc(
                    brush = brush,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = com.calorietracker.app.domain.nutrition.NutritionMath.formatKcal(intake),
                style = BigNumberStyle,
                color = CalorieColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label.format(target.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.TextSecondary,
            )
            if (!hasData) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "还没有记录",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    color = CalorieColors.TextSecondary,
                )
            }
            content()
        }
    }
}
