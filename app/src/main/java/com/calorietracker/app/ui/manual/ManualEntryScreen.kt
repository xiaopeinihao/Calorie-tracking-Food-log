package com.calorietracker.app.ui.manual

import androidx.activity.compose.BackHandler

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.data.local.entity.FoodItem
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.ui.components.AppCard
import com.calorietracker.app.ui.components.EmptyState
import com.calorietracker.app.ui.components.GramsStepper
import com.calorietracker.app.ui.components.PrimaryActionButton
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

@Composable
fun ManualEntryScreen(
    onBack: () -> Unit,
    onItemAdded: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val grams by viewModel.grams.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    // 系统返回键 / 手势也要清掉「替换某条」的标记，
    // 否则下次普通的「+ 手动添加食物」会误替换上一条
    BackHandler {
        viewModel.cancelResolve()
        onBack()
    }

    Scaffold(containerColor = CalorieColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        .clickable {
                            viewModel.cancelResolve()
                            onBack()
                        }
                        .padding(horizontal = 10.dp),
                )
                Text(
                    text = "手动录入",
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))

            SearchField(query = query, onQueryChange = viewModel::onQueryChange)
            Spacer(Modifier.height(16.dp))

            if (selected == null) {
                Text(
                    text = if (query.isBlank()) "常见食物" else "搜索结果 ${results.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    EmptyState(
                        title = "没找到这个食物",
                        subtitle = "换个别名试试，比如「白米饭」「西红柿」。\n食物库只收录有权威出处的条目，不凭空生成。",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results, key = { it.id }) { food ->
                            FoodRow(food = food, onClick = { viewModel.select(food) })
                        }
                    }
                }
            } else {
                val food = selected!!
                // 选中食物后是一段高度固定的表单，本身不滚动；
                // 小屏 + 键盘弹起时「加入本餐」会被挤出屏幕，所以给它一个独立滚动容器
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        PortionEditor(
                            food = food,
                            grams = grams,
                            onGramsChange = viewModel::setGrams,
                            kcal = viewModel.currentSummary().kcal,
                            carbs = viewModel.currentSummary().carbs,
                            protein = viewModel.currentSummary().protein,
                            fat = viewModel.currentSummary().fat,
                            onBackToSearch = viewModel::clearSelection,
                            confirmEnabled = !busy,
                            onConfirm = { viewModel.confirm(onItemAdded) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CalorieColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (query.isEmpty()) {
            Text(
                text = "搜索食物，如「白米饭」「鸡蛋」",
                style = MaterialTheme.typography.bodyMedium,
                color = CalorieColors.TextSecondary,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = CalorieColors.TextPrimary,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FoodRow(food: FoodItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CalorieColors.Surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                style = MaterialTheme.typography.titleMedium,
                color = CalorieColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${food.category} · 每 100g ${NutritionMath.formatKcal(food.kcalPer100g)} 千卡 · ${food.source}",
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.TextSecondary,
            )
        }
        Text(
            text = "${NutritionMath.formatKcal(food.kcalPer100g)}",
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.Primary,
        )
    }
}

@Composable
private fun PortionEditor(
    food: FoodItem,
    grams: Int,
    onGramsChange: (Int) -> Unit,
    kcal: Double,
    carbs: Double,
    protein: Double,
    fat: Double,
    onBackToSearch: () -> Unit,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppCard {
            Column {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${food.source}${food.sourceRef?.let { " · 条目 $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "份量",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalorieColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    GramsStepper(value = grams, onValueChange = onGramsChange, step = 10)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Metric("热量", "${NutritionMath.formatKcal(kcal)} 千卡")
                    Metric("碳水", "${NutritionMath.formatGram(carbs)}g")
                    Metric("蛋白", "${NutritionMath.formatGram(protein)}g")
                    Metric("脂肪", "${NutritionMath.formatGram(fat)}g")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryActionButton(
            text = "加入本餐",
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(23.dp))
                .clickable { onBackToSearch() },
            contentAlignment = Alignment.Center,
        ) {
            Text("换一个食物", style = MaterialTheme.typography.bodyMedium, color = CalorieColors.TextSecondary)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CalorieColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = CalorieColors.TextPrimary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun ManualEntryPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            SearchField(query = "", onQueryChange = {})
            Spacer(Modifier.height(16.dp))
            PortionEditor(
                food = FoodItem(
                    id = "cfct:012401x",
                    name = "米饭（蒸，代表值）",
                    category = "谷薯",
                    kcalPer100g = 116.0,
                    carbsPer100g = 25.9,
                    proteinPer100g = 2.6,
                    fatPer100g = 0.3,
                    commonServing = "一碗约 200g",
                    commonServingGrams = 200,
                    source = "中国食物成分表 标准版（第6版）",
                    sourceRef = "012401x",
                    alias = "白米饭,米饭",
                    cooked = true,
                ),
                grams = 200,
                onGramsChange = {},
                kcal = 232.0,
                carbs = 51.8,
                protein = 5.2,
                fat = 0.6,
                onBackToSearch = {},
                confirmEnabled = true,
                onConfirm = {},
            )
        }
    }
}

@Suppress("unused")
private val previewColor: Color = Color.Unspecified
