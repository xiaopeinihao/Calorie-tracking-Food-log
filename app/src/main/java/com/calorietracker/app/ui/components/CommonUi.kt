package com.calorietracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calorietracker.app.ui.theme.CalorieColors

/**
 * 尺寸约定：所有承载文字的控件高度都用 `heightIn(min = ...)` 而不是固定 `height(...)`。
 * 用户把系统字体调大（无障碍「特大」）时，控件随文字一起长高，不会被裁掉半行字；
 * 正常字号下高度与设计稿完全一致。
 */

/** 统一卡片：白底、24dp 圆角、无重阴影（只靠底色差分层级） */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(20.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CalorieColors.Surface,
        content = {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        },
    )
}

/** 页面大标题（28sp Bold） */
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = CalorieColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 卡片小标题（16sp SemiBold） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = CalorieColors.TextPrimary,
        modifier = modifier,
    )
}

/** 胶囊分段控件：日 / 周 / 月 */
@Composable
fun PillSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CalorieColors.Surface)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) CalorieColors.Primary else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else CalorieColors.TextSecondary,
                )
            }
        }
    }
}

/** 主按钮：28dp 圆角、实心薄荷绿 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) CalorieColors.Primary else CalorieColors.Divider)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else CalorieColors.TextSecondary,
        )
    }
}

/** 次按钮：白底 + 1dp 描边 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface)
            .border(1.dp, CalorieColors.Divider, RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CalorieColors.TextPrimary)
    }
}

/** 份量步进器 − n + */
@Composable
fun GramsStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 10,
    min: Int = 5,
    max: Int = 2000,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepButton(
            enabled = value > min,
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
            isPlus = false,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = CalorieColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 34.dp)
                .heightIn(min = 28.dp),
        )
        StepButton(
            enabled = value < max,
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
            isPlus = true,
        )
    }
}

@Composable
private fun StepButton(enabled: Boolean, onClick: () -> Unit, isPlus: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = if (enabled) CalorieColors.Primary else CalorieColors.Divider,
                shape = CircleShape,
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlus) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = if (isPlus) "增加" else "减少",
            tint = if (enabled) CalorieColors.Primary else CalorieColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 虚线「+ 手动添加食物」卡片 */
@Composable
fun DashedAddCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stroke = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CalorieColors.Surface)
            .drawBehind {
                drawRoundRect(
                    color = CalorieColors.Primary.copy(alpha = 0.45f),
                    style = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                )
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.Primary,
        )
    }
}

/** 空态 / 错误态统一组件 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = CalorieColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryActionButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
        }
    }
}
