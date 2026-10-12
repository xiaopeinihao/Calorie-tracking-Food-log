package com.calorietracker.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.data.local.dao.MealWithItems
import com.calorietracker.app.data.local.entity.EntryItem
import com.calorietracker.app.data.local.entity.MealEntry
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.ui.components.EmptyState
import com.calorietracker.app.ui.components.PageTitle
import com.calorietracker.app.ui.home.emoji
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

@Composable
fun RecordScreen(
    onOpenConfirm: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalorieColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        PageTitle("记录")
        Spacer(Modifier.height(16.dp))

        if (days.isEmpty()) {
            EmptyState(
                title = "还没有任何记录",
                subtitle = "回首页点「拍照记录」，或者手动录一条。",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(days, key = { it.dateKey }) { day ->
                    DayCard(
                        day = day,
                        isExpanded = expanded.contains(day.dateKey),
                        onToggle = { viewModel.toggle(day.dateKey) },
                        onDeleteMeal = viewModel::delete,
                        onEditMeal = { mealId ->
                            viewModel.beginEdit(mealId, onOpenConfirm)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    day: RecordDay,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onEditMeal: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${day.meals.size} 条记录 · 碳水 ${NutritionMath.formatGram(day.summary.carbs)}g · 蛋白 ${NutritionMath.formatGram(day.summary.protein)}g · 脂肪 ${NutritionMath.formatGram(day.summary.fat)}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
            }
            Text(
                text = "${NutritionMath.formatKcal(day.summary.kcal)} 千卡",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CalorieColors.Primary,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (isExpanded) "⌃" else "⌄",
                style = MaterialTheme.typography.titleMedium,
                color = CalorieColors.TextSecondary,
            )
        }

        if (isExpanded) {
            day.meals.forEach { meal ->
                MealSwipeRow(
                    meal = meal,
                    onDelete = { onDeleteMeal(meal.meal.id) },
                    onEdit = { onEditMeal(meal.meal.id) },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MealSwipeRow(
    meal: MealWithItems,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    // 左滑**不直接删**：记录是用户唯一的凭据，一次误滑就永久丢掉一餐，
    // 而且这里没有任何撤销入口（结果页删除都有二次确认，这里以前没有）。
    var askDelete by remember { mutableStateOf(false) }

    if (askDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("删除这一餐？") },
            text = {
                Text("「${meal.meal.mealType}」的 ${meal.items.size} 条食物都会被删掉，删除后无法恢复。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        askDelete = false
                        onDelete()
                    },
                ) { Text("删除", color = CalorieColors.Accent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askDelete = false }) { Text("取消") }
            },
        )
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // 返回 false → 卡片滑回原位，只把「要不要删」交给用户确认
                askDelete = true
                false
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CalorieColors.Accent),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CalorieColors.PrimarySoft)
                .clickable { onEdit() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = MealType.fromLabel(meal.meal.mealType).emoji())
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${meal.meal.mealType} · ${meal.items.size} 项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColors.TextPrimary,
                )
                Text(
                    text = meal.items.joinToString("、") { it.foodName }.take(40),
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${NutritionMath.formatKcal(meal.totalKcal)} 千卡",
                style = MaterialTheme.typography.bodyMedium,
                color = CalorieColors.TextPrimary,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun RecordScreenPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PageTitle("记录")
            Spacer(Modifier.height(16.dp))
            val meal = MealWithItems(
                meal = MealEntry(
                    id = 1,
                    date = "2026-09-30",
                    createdAt = 0L,
                    mealType = "午餐",
                    source = "拍照",
                ),
                items = listOf(
                    EntryItem(
                        mealEntryId = 1,
                        foodName = "番茄炒蛋",
                        grams = 180,
                        kcal = 174.0,
                        carbs = 5.2,
                        protein = 8.6,
                        fat = 11.0,
                    ),
                ),
            )
            DayCard(
                day = RecordDay(
                    dateKey = "2026-09-30",
                    label = "今天",
                    summary = NutritionSummary(496.0, 60.0, 20.0, 16.0),
                    meals = listOf(meal),
                ),
                isExpanded = true,
                onToggle = {},
                onDeleteMeal = {},
                onEditMeal = {},
            )
        }
    }
}
