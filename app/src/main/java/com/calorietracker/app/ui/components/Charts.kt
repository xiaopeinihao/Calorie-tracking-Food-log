package com.calorietracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calorietracker.app.ui.theme.CalorieColors

/**
 * 每日摄入柱状图：圆角竖柱 + 薄荷绿渐变，目标线用浅灰虚线。
 * 手写 Canvas，不引第三方图表库。
 *
 * @param values 每天的热量（千卡），顺序即从左到右
 * @param labels 柱下的短标签（一/二/三…）
 * @param target 目标线；<= 0 时不画
 * @param highlightIndex 高亮柱（暖橙），-1 表示不高亮
 */
@Composable
fun DailyIntakeBarChart(
    values: List<Double>,
    labels: List<String>,
    target: Double,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 160.dp,
    highlightIndex: Int = -1,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (values.isEmpty()) return@Canvas

            val maxValue = maxOf(values.maxOrNull() ?: 0.0, target, 1.0) * 1.15
            val count = values.size
            val slot = size.width / count
            val barWidth = slot * 0.42f
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val bottom = size.height

            // 目标虚线
            if (target > 0) {
                val y = bottom - (target / maxValue * bottom).toFloat()
                drawLine(
                    color = Color(0xFFD8DCE0),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                )
            }

            values.forEachIndexed { index, value ->
                val barHeight = (value / maxValue * bottom).toFloat().coerceAtLeast(0f)
                if (barHeight <= 0f) return@forEachIndexed
                val left = slot * index + (slot - barWidth) / 2f
                val top = bottom - barHeight
                val isHighlight = index == highlightIndex
                val brush = if (isHighlight) {
                    Brush.verticalGradient(listOf(CalorieColors.Accent, CalorieColors.Accent.copy(alpha = 0.75f)))
                } else {
                    Brush.verticalGradient(listOf(CalorieColors.Primary, CalorieColors.Primary.copy(alpha = 0.55f)))
                }
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == highlightIndex) CalorieColors.Accent else CalorieColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** 营养构成一行：名称 + 进度条 + 「碳水 168g · 46%」 */
@Composable
fun MacroProgressRow(
    name: String,
    grams: Double,
    percentOfEnergy: Int,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CalorieColors.Divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$name ${com.calorietracker.app.domain.nutrition.NutritionMath.formatGram(grams)}g · $percentOfEnergy%",
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.TextPrimary,
                // 原来是死宽 140dp：系统字体放大到 1.5~2 倍时「蛋白质 92.5g · 100%」会折行/被裁。
                // 改成最小 140dp（常规字号下与原来完全一致、右对齐不变），最多长到 220dp，
                // 再长就省略号；左侧进度条有 weight(1f)，不会被挤没。
                modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/** 目标完成度细进度条（首页三大营养素下方用） */
@Composable
fun ThinProgress(
    fraction: Float,
    color: Color = CalorieColors.Primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(CalorieColors.Divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** 三种营养素横向小指标（首页圆环下方：碳水 / 蛋白 / 脂肪） */
@Composable
fun MacroTriple(
    carbs: Double,
    protein: Double,
    fat: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MacroCell("碳水", carbs, Modifier.weight(1f))
        VerticalDividerBlock()
        MacroCell("蛋白", protein, Modifier.weight(1f))
        VerticalDividerBlock()
        MacroCell("脂肪", fat, Modifier.weight(1f))
    }
}

@Composable
private fun MacroCell(label: String, grams: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${com.calorietracker.app.domain.nutrition.NutritionMath.formatGram(grams)}g",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = CalorieColors.TextPrimary,
        )
    }
}

@Composable
private fun VerticalDividerBlock() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 34.dp)
            .background(CalorieColors.Divider),
    )
}

/** 打卡条：已连续记录 N 天 */
@Composable
fun StreakBanner(
    days: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CalorieColors.PrimaryContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🔥", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "已连续记录 $days 天",
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextSecondary,
        )
    }
}

/** 环形描边占位（用于「保存中」之类的轻量状态） */
@Composable
fun RingOutline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Transparent),
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            drawCircle(
                color = CalorieColors.Primary,
                style = Stroke(width = 3f),
            )
        }
    }
}
