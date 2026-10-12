package com.calorietracker.app.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.data.repository.Gender
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.ui.components.AppCard
import com.calorietracker.app.ui.components.PageTitle
import com.calorietracker.app.ui.components.SectionTitle
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    onOpenAiSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val body by viewModel.bodyProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalorieColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        PageTitle("我的")
        Spacer(Modifier.height(16.dp))

        // 每日热量目标
        AppCard {
            Column {
                SectionTitle("每日热量目标")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.targetKcal.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = CalorieColors.TextPrimary,
                    )
                    Text(
                        text = "千卡",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                Slider(
                    value = state.targetKcal.toFloat(),
                    onValueChange = { viewModel.setTargetKcal(it.roundToInt()) },
                    onValueChangeFinished = viewModel::saveTarget,
                    valueRange = 1000f..3500f,
                    colors = SliderDefaults.colors(
                        thumbColor = CalorieColors.Primary,
                        activeTrackColor = CalorieColors.Primary,
                        inactiveTrackColor = CalorieColors.Divider,
                    ),
                )
                Text(
                    text = "碳水 ${state.targetCarbs}g · 蛋白 ${state.targetProtein}g · 脂肪 ${state.targetFat}g（按 50% / 20% / 30% 供能拆分）",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // 身体数据 → 基础代谢
        AppCard {
            Column {
                SectionTitle("身高体重 · 基础代谢")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "可选填。填了就能用 Mifflin-St Jeor 公式算出基础代谢与推荐摄入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "身高 cm",
                        value = body.heightCm?.toString() ?: "",
                        modifier = Modifier.weight(1f),
                        onValueChange = { viewModel.updateBody(heightCm = it.toIntOrNull()) },
                    )
                    NumberField(
                        label = "体重 kg",
                        value = body.weightKg?.let { NutritionMath.formatGram(it) } ?: "",
                        modifier = Modifier.weight(1f),
                        onValueChange = { viewModel.updateBody(weightKg = it.toDoubleOrNull()) },
                    )
                    NumberField(
                        label = "年龄",
                        value = body.age?.toString() ?: "",
                        modifier = Modifier.weight(1f),
                        onValueChange = { viewModel.updateBody(age = it.toIntOrNull()) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Gender.entries.forEach { gender ->
                        SelectableChip(
                            text = gender.label,
                            selected = body.gender == gender,
                            onClick = { viewModel.updateBody(gender = gender) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SelectableChip(
                        text = "久坐",
                        selected = body.activityFactor <= 1.4,
                        onClick = { viewModel.updateBody(activityFactor = 1.375) },
                    )
                    SelectableChip(
                        text = "常活动",
                        selected = body.activityFactor > 1.4,
                        onClick = { viewModel.updateBody(activityFactor = 1.55) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBlock(
                        label = "基础代谢 BMR",
                        value = state.bmr?.let { "${NutritionMath.formatKcal(it)} 千卡" } ?: "待填写",
                        modifier = Modifier.weight(1f),
                    )
                    MetricBlock(
                        label = "每日总消耗 TDEE",
                        value = state.tdee?.let { "${NutritionMath.formatKcal(it)} 千卡" } ?: "待填写",
                        modifier = Modifier.weight(1f),
                    )
                }
                // 数值明显不合理时明确拦一下：不给推荐值，也不让人以为算出的是对的
                state.bodyWarning?.let { warning ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "$warning；修正后才给推荐摄入",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.Accent,
                    )
                }
                Spacer(Modifier.height(14.dp))
                val suggested = state.suggestedKcal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (suggested != null) CalorieColors.PrimaryContainer else CalorieColors.Divider)
                        .clickable(enabled = suggested != null) { viewModel.applySuggested() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (suggested != null) "用推荐值 $suggested 千卡作为目标" else "填写身高体重后可自动推荐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (suggested != null) CalorieColors.Primary else CalorieColors.TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // AI 识别
        AppCard {
            Column {
                SectionTitle("AI 识别")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "拍照识别与一句话记账都走大模型。填一个 API Key 就能用真 AI 识别。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                SettingRow(
                    title = "图片识别（拍照）",
                    subtitle = state.aiVisionStatus,
                    onClick = onOpenAiSettings,
                )
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    title = "文本解析（一句话）",
                    subtitle = state.aiTextStatus,
                    onClick = onOpenAiSettings,
                )
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    title = "AI 设置",
                    subtitle = "服务商 / API Key / 模型 / 测试连接",
                    onClick = onOpenAiSettings,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // 数据与关于
        AppCard {
            Column {
                SectionTitle("数据")
                Spacer(Modifier.height(14.dp))
                SettingRow(
                    title = "导出记录为 CSV",
                    subtitle = "含每条明细与出处，可直接在 Excel 里核对",
                    onClick = viewModel::exportCsv,
                )
                Spacer(Modifier.height(12.dp))
                SettingRow(
                    title = "分享最近一次导出",
                    subtitle = state.exportUri ?: "还没有导出过",
                    enabled = state.exportUri != null,
                    onClick = {
                        val uri = state.exportUri ?: return@SettingRow
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri.toUri())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享 CSV"))
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        AppCard {
            Column {
                SectionTitle("关于")
                Spacer(Modifier.height(10.dp))
                AboutLine("食物库条目", "${state.foodCount} 条（每 100g 可食部）")
                AboutLine("主数据源", "中国食物成分表 标准版（第6版）")
                AboutLine("第二数据源", "USDA FoodData Central · SR Legacy")
                AboutLine("AI 估算", "单条标注「AI 估算」，与权威数据分开显示")
                AboutLine("家常菜口径", "按显式配比用成分表食材值推算，配比可查")
                AboutLine("版本", "1.0.0 · minSdk 29 / targetSdk 36")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "本 App 不手工编写任何营养数值：所有热量与营养素都来自上述权威数据库，" +
                        "每条食物都能按条目号回溯核对。识别服务只负责猜「是什么、多重」，" +
                        "热量永远由本地食物库换算。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
            }
        }

        if (state.message != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.message!!,
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.Primary,
                modifier = Modifier.clickable { viewModel.consumeMessage() },
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CalorieColors.Background)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColors.TextSecondary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { text ->
                    onValueChange(text.filter { it.isDigit() || it == '.' }.take(6))
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                textStyle = TextStyle(
                    color = CalorieColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) CalorieColors.PrimaryContainer else CalorieColors.Background)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CalorieColors.Primary else CalorieColors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextPrimary,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CalorieColors.Background)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) CalorieColors.TextPrimary else CalorieColors.TextSecondary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "›", style = MaterialTheme.typography.titleMedium, color = CalorieColors.TextSecondary)
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextPrimary,
            // value 可能是很长的来源说明（如 "USDA FoodData Central · SR Legacy"），
            // 不给 weight 的话在窄屏 + 大字号下会横向溢出卡片
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("unused")
private val previewAiStatus = ProfileUiState(
    aiVisionStatus = "智谱 GLM · glm-4v-flash",
    aiTextStatus = "DeepSeek · deepseek-chat",
)

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun ProfileScreenPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PageTitle("我的")
            Spacer(Modifier.height(16.dp))
            AppCard {
                Column {
                    SectionTitle("每日热量目标")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1600",
                        style = MaterialTheme.typography.displayLarge,
                        color = CalorieColors.TextPrimary,
                    )
                    Text(
                        "碳水 200g · 蛋白 80g · 脂肪 53g（按 50% / 20% / 30% 供能拆分）",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AppCard {
                Column {
                    SectionTitle("身高体重 · 基础代谢")
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBlock("基础代谢 BMR", "1287 千卡", Modifier.weight(1f))
                        MetricBlock("每日总消耗 TDEE", "1770 千卡", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}


