package com.calorietracker.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.ui.components.AppCard
import com.calorietracker.app.ui.components.DailyIntakeBarChart
import com.calorietracker.app.ui.components.MacroProgressRow
import com.calorietracker.app.ui.components.PageTitle
import com.calorietracker.app.ui.components.PillSegmented
import com.calorietracker.app.ui.components.SectionTitle
import com.calorietracker.app.ui.components.StreakBanner
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalorieColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        PageTitle("趋势")
        Spacer(Modifier.height(16.dp))

        PillSegmented(
            options = listOf(StatPeriod.DAY.label, StatPeriod.WEEK.label, StatPeriod.MONTH.label),
            selectedIndex = state.period.ordinal,
            onSelect = { viewModel.selectPeriod(StatPeriod.entries[it]) },
        )
        Spacer(Modifier.height(16.dp))

        // 卡片一：日均摄入 + 环比
        AppCard {
            Column {
                Text(
                    text = state.headlineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        // 没有记录时**不要显示「0 千卡」**：那是个确定的假数字，
                        // 而同一屏下面的柱状图卡已经写着「这段时间还没有记录」，两处必须一致。
                        text = if (state.hasData) NutritionMath.formatKcal(state.headlineKcal) else "—",
                        style = MaterialTheme.typography.displayLarge,
                        color = CalorieColors.TextPrimary,
                    )
                    Spacer(Modifier.height(0.dp))
                    if (state.hasData) {
                        Text(
                            text = "千卡",
                            style = MaterialTheme.typography.labelSmall,
                            color = CalorieColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.hasData) {
                        DeltaBadge(label = state.deltaLabel, percent = state.deltaPercent)
                    } else {
                        Text(
                            text = "还没有记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = CalorieColors.TextSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 卡片二：每日摄入柱状图
        AppCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("每日摄入", modifier = Modifier.weight(1f))
                    Text(
                        text = "目标 ${state.targetKcal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (state.chartValues.all { it == 0.0 }) {
                    Text(
                        text = "这段时间还没有记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalorieColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    DailyIntakeBarChart(
                        values = state.chartValues,
                        labels = state.chartLabels,
                        target = state.targetKcal.toDouble(),
                        highlightIndex = state.highlightIndex,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 卡片三：营养构成
        val macro = state.macro
        val carbsKcal = macro.carbs * 4.0
        val proteinKcal = macro.protein * 4.0
        val fatKcal = macro.fat * 9.0
        val totalKcal = carbsKcal + proteinKcal + fatKcal
        AppCard {
            Column {
                SectionTitle("营养构成")
                Spacer(Modifier.height(16.dp))
                // 没有数据时不能显示「0g · 0%」：那是把「不知道」说成了「就是 0」。
                // 有记录（但只记了热量、宏量缺失）时同理：那种情况分母 totalKcal 会是 0。
                if (!state.hasData || totalKcal <= 0.0) {
                    Text(
                        text = "这段时间还没有记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalorieColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    MacroProgressRow(
                        name = "碳水",
                        grams = macro.carbs,
                        percentOfEnergy = percent(carbsKcal, totalKcal),
                        fraction = fraction(carbsKcal, totalKcal),
                        color = CalorieColors.Carbs,
                    )
                    Spacer(Modifier.height(14.dp))
                    MacroProgressRow(
                        name = "蛋白质",
                        grams = macro.protein,
                        percentOfEnergy = percent(proteinKcal, totalKcal),
                        fraction = fraction(proteinKcal, totalKcal),
                        color = CalorieColors.Protein,
                    )
                    Spacer(Modifier.height(14.dp))
                    MacroProgressRow(
                        name = "脂肪",
                        grams = macro.fat,
                        percentOfEnergy = percent(fatKcal, totalKcal),
                        fraction = fraction(fatKcal, totalKcal),
                        color = CalorieColors.Fat,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        StreakBanner(days = state.streakDays)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeltaBadge(label: String, percent: Int?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        if (percent == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = CalorieColors.TextSecondary,
            )
        } else {
            // percent == 0 时原来会显示「0% ↓」并染成下降色，等于凭空报了个下降。
            // 持平就老实说持平。
            Text(
                text = when {
                    percent == 0 -> "持平"
                    percent > 0 -> "$percent% ↑"
                    else -> "$percent% ↓"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    percent == 0 -> CalorieColors.TextSecondary
                    percent > 0 -> CalorieColors.Accent
                    else -> CalorieColors.Primary
                },
            )
        }
    }
}

private fun percent(part: Double, total: Double): Int =
    if (total <= 0) 0 else Math.round(part / total * 100).toInt()

private fun fraction(part: Double, total: Double): Float =
    if (total <= 0) 0f else (part / total).toFloat()

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun StatsScreenPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PageTitle("趋势")
            Spacer(Modifier.height(16.dp))
            PillSegmented(listOf("日", "周", "月"), 1, {})
            Spacer(Modifier.height(16.dp))
            AppCard {
                Column {
                    Text("本周日均摄入", style = MaterialTheme.typography.labelSmall, color = CalorieColors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "1520",
                            style = MaterialTheme.typography.displayLarge,
                            color = CalorieColors.TextPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        DeltaBadge("较上周", -8)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AppCard {
                Column {
                    SectionTitle("每日摄入")
                    Spacer(Modifier.height(16.dp))
                    DailyIntakeBarChart(
                        values = listOf(1650.0, 1580.0, 1920.0, 1490.0, 1720.0, 1380.0, 900.0),
                        labels = listOf("一", "二", "三", "四", "五", "六", "日"),
                        target = 1600.0,
                        highlightIndex = 2,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AppCard {
                Column {
                    SectionTitle("营养构成")
                    Spacer(Modifier.height(16.dp))
                    MacroProgressRow("碳水", 168.0, 46, 0.46f, CalorieColors.Carbs)
                    Spacer(Modifier.height(14.dp))
                    MacroProgressRow("蛋白质", 92.0, 28, 0.28f, CalorieColors.Protein)
                    Spacer(Modifier.height(14.dp))
                    MacroProgressRow("脂肪", 48.0, 26, 0.26f, CalorieColors.Fat)
                }
            }
            Spacer(Modifier.height(16.dp))
            StreakBanner(12)
        }
    }
}

@Suppress("unused")
private val previewMacro = NutritionSummary(1520.0, 168.0, 92.0, 48.0)

@Suppress("unused")
private val previewBox: @Composable () -> Unit = { Box(Modifier) }
