package com.calorietracker.app.ui.quickinput

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

private val EXAMPLES = listOf(
    "午饭：番茄炒蛋配一碗米饭",
    "早饭一个水煮蛋，一杯全脂牛奶",
    "刚喝了一杯拿铁，吃了一小把杏仁",
    "晚餐 150g 鸡胸肉、一份清炒西兰花",
)

/**
 * 一句话记一餐。与拍照识别共用同一条 AI 解析链路（BeeCount 的「多渠道共享一条流水线」）。
 *
 * 这里**不自己做语音输入**：现在的输入法都带语音转文字，键盘上的麦克风更顺手，
 * 也没必要为它多申请录音权限、多接一个语音模型。用户「说」完落到输入框里，走的还是这条解析链路。
 */
@Composable
fun QuickInputScreen(
    onBack: () -> Unit,
    onOpenConfirm: () -> Unit,
    onOpenAiSettings: () -> Unit,
    viewModel: QuickInputViewModel = hiltViewModel(),
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val parsing by viewModel.parsing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val needsAiConfig by viewModel.needsAiConfig.collectAsStateWithLifecycle()

    val mealType = MealType.byHour(com.calorietracker.app.util.DateUtil.hourOfDay())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalorieColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium,
                color = CalorieColors.TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp),
            )
            Text(
                text = "一句话记一餐",
                style = MaterialTheme.typography.titleMedium,
                color = CalorieColors.TextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "用大白话说吃了什么就行（想用语音直接按键盘上的麦克风），AI 会拆成条目并估算份量，热量仍然优先取本地权威食物库。",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = viewModel::onTextChange,
            placeholder = {
                Text(
                    "比如：午饭番茄炒蛋配一碗米饭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColors.TextSecondary,
                )
            },
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CalorieColors.Surface,
                unfocusedContainerColor = CalorieColors.Surface,
                focusedBorderColor = CalorieColors.Primary,
                unfocusedBorderColor = CalorieColors.Divider,
                focusedTextColor = CalorieColors.TextPrimary,
                unfocusedTextColor = CalorieColors.TextPrimary,
                cursorColor = CalorieColors.Primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (parsing) CalorieColors.Divider else CalorieColors.Primary)
                .clickable(enabled = !parsing) { viewModel.parse(mealType, onOpenConfirm) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (parsing) "AI 解析中…" else "解析并记录",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CalorieColors.PrimaryContainer)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.TextPrimary,
                    )
                    if (needsAiConfig) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "去 AI 设置 ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = CalorieColors.Primary,
                            modifier = Modifier.clickable { onOpenAiSettings() },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("试试这些说法", style = MaterialTheme.typography.labelSmall, color = CalorieColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        EXAMPLES.forEach { example ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CalorieColors.Surface)
                    .clickable { viewModel.appendText(example) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColors.TextPrimary,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun QuickInputPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(20.dp),
        ) {
            Text("一句话记一餐", style = MaterialTheme.typography.headlineMedium, color = CalorieColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CalorieColors.Surface)
                    .padding(16.dp),
            ) {
                Text("午饭：番茄炒蛋配一碗米饭", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            EXAMPLES.take(2).forEach { example ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CalorieColors.Surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(example, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
