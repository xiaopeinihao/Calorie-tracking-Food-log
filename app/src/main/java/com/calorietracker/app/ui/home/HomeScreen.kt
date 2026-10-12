package com.calorietracker.app.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.data.local.entity.DailyGoal
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.ui.components.AppCard
import com.calorietracker.app.ui.components.CalorieRing
import com.calorietracker.app.ui.components.MacroTriple
import com.calorietracker.app.ui.components.PageTitle
import com.calorietracker.app.ui.components.SectionTitle
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onOpenConfirm: () -> Unit,
    onOpenQuickInput: () -> Unit,
    onOpenProfile: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = CalorieColors.Background,
        floatingActionButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 不想拍照/不方便拍照时的一句话入口
                SmallFloatingActionButton(
                    onClick = onOpenQuickInput,
                    containerColor = CalorieColors.Surface,
                    contentColor = CalorieColors.Primary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        Icons.Filled.EditNote,
                        contentDescription = "一句话记一餐",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                ExtendedFloatingActionButton(
                    onClick = onOpenCamera,
                    containerColor = CalorieColors.Primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    text = {
                        Text(
                            text = "拍照记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PageTitle(
                text = "今天",
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CalorieColors.Divider)
                            // 右上角这个「人像」用户一定会去点（它还带着「我的」的无障碍语义）。
                            // 以前只画不响应，点了完全没反应 —— 现在与底部 Tab 一样跳到「我的」。
                            .clickable(onClick = onOpenProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "我的",
                            tint = CalorieColors.TextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(20.dp))
            CalorieCard(state = state)

            Spacer(Modifier.height(24.dp))
            SectionTitle("今日餐次")
            Spacer(Modifier.height(12.dp))

            state.groups.forEach { group ->
                MealRow(
                    group = group,
                    onClick = { viewModel.openMeal(group.type, onOpenConfirm) },
                )
                Spacer(Modifier.height(12.dp))
            }

            // 给悬浮按钮留出空间
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CalorieCard(state: HomeUiState) {
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp, horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CalorieRing(
                intake = state.summary.kcal,
                target = state.goal.targetKcal.toDouble(),
            )
        }
        Spacer(Modifier.height(24.dp))
        MacroTriple(
            carbs = state.summary.carbs,
            protein = state.summary.protein,
            fat = state.summary.fat,
        )
    }
}

@Composable
private fun MealRow(group: MealGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CalorieColors.PrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = group.type.emoji(), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(14.dp))
        Text(
            text = group.type.label,
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (group.itemCount == 0) "未记录"
            else "${NutritionMath.formatKcal(group.kcal)} 千卡",
            style = MaterialTheme.typography.bodyMedium,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextSecondary,
        )
    }
}

internal fun MealType.emoji(): String = when (this) {
    MealType.BREAKFAST -> "🥣"
    MealType.LUNCH -> "🍚"
    MealType.DINNER -> "🍲"
    MealType.SNACK -> "🥤"
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun HomeScreenPreview() {
    CalorieTrackerTheme {
        Scaffold(containerColor = CalorieColors.Background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                PageTitle("今天")
                Spacer(Modifier.height(20.dp))
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CalorieRing(intake = 1240.0, target = 1600.0)
                    }
                    Spacer(Modifier.height(20.dp))
                    MacroTriple(carbs = 132.0, protein = 78.0, fat = 42.0)
                }
                Spacer(Modifier.height(24.dp))
                SectionTitle("今日餐次")
                Spacer(Modifier.height(12.dp))
                listOf(
                    MealGroup(MealType.BREAKFAST, 420.0, 1L, 2),
                    MealGroup(MealType.LUNCH, 560.0, 2L, 3),
                    MealGroup(MealType.DINNER, 0.0, null, 0),
                    MealGroup(MealType.SNACK, 260.0, 3L, 1),
                ).forEach {
                    MealRow(group = it, onClick = {})
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Suppress("unused")
private val previewGoal = DailyGoal.default()
private val previewSummary = NutritionSummary(kcal = 1240.0, carbs = 132.0, protein = 78.0, fat = 42.0)

@Suppress("unused")
private val previewTitleWeight = FontWeight.Bold
