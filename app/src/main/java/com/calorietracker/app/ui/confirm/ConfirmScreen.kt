package com.calorietracker.app.ui.confirm

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import com.calorietracker.app.domain.session.RecognitionState
import com.calorietracker.app.ui.components.DashedAddCard
import com.calorietracker.app.ui.components.EmptyState
import com.calorietracker.app.ui.components.GramsStepper
import com.calorietracker.app.ui.components.PrimaryActionButton
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

@Composable
fun ConfirmScreen(
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onAddManual: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ConfirmViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val editingMealId by viewModel.editingMealId.collectAsStateWithLifecycle()
    val dateKey by viewModel.dateKey.collectAsStateWithLifecycle()
    var actionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ConfirmEvent.Saved -> onSaved()
                ConfirmEvent.Deleted -> onDeleted()
            }
        }
    }

    val isEditing = editingMealId != null
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var askDelete by remember { mutableStateOf(false) }

    // 「+ 手动添加食物」要先清掉「替换某条」的标记，
    // 否则上次点过「从食物库补上」再返回，这次添加会误把上一条替换掉
    val openManual: () -> Unit = { viewModel.addManual(onAddManual) }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("删除这一餐？") },
            text = { Text("这一餐的 ${items.size} 条食物都会被删掉，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    viewModel.deleteExistingMeal()
                }) { Text("删除", color = CalorieColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = CalorieColors.Background,
        bottomBar = {
            if (items.isNotEmpty()) {
                BottomSummaryBar(
                    total = viewModel.total.kcal,
                    busy = busy,
                    pending = items.count { it.needsNutrition },
                    onConfirm = { viewModel.save() },
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
            ResultTopBar(
                title = if (isEditing) "记录详情" else "识别结果",
                rightText = if (isEditing) "删除" else "重新拍",
                onBack = onBack,
                onRight = {
                    // 删除是不可逆动作：先问一句，别让误触直接抹掉一餐
                    if (isEditing) askDelete = true else onRetake()
                },
            )
            Spacer(Modifier.height(16.dp))

            val notice by viewModel.notice.collectAsStateWithLifecycle()
            if (!notice.isNullOrBlank()) {
                NoticeBanner(text = notice!!, onDismiss = { viewModel.dismissNotice() })
                Spacer(Modifier.height(12.dp))
            }
            if (actionMessage != null) {
                NoticeBanner(text = actionMessage!!, onDismiss = { actionMessage = null })
                Spacer(Modifier.height(12.dp))
            }

            val photo = photoUri
            if (photo != null) {
                PhotoBanner(
                    model = photo,
                    badge = if (isEditing) "历史记录" else "刚刚拍摄",
                )
                Spacer(Modifier.height(20.dp))
            }

            when (val current = state) {
                is RecognitionState.Failure -> {
                    if (items.isEmpty()) {
                        EmptyState(
                            title = "识别失败了",
                            subtitle = current.message,
                            actionText = "手动录入",
                            onAction = openManual,
                        )
                    } else {
                        // 已经有条目时，失败信息降级成一条提示：不能把用户已经录好的东西盖住
                        InlineWarning(message = current.message, onAdd = openManual)
                        Spacer(Modifier.height(16.dp))
                        ItemList(
                            items = items,
                            isEditing = isEditing,
                            mealType = mealType,
                            dateKey = dateKey,
                            onMealTypeSelect = viewModel::setMealType,
                            onGramsChange = viewModel::setGrams,
                            onRemove = viewModel::remove,
                            onAddManual = openManual,
                            onResolve = { index -> viewModel.beginResolve(index, onAddManual) },
                            onSaveToLibrary = { index -> viewModel.saveToLibrary(index) { actionMessage = it } },
                        )
                    }
                }

                else -> ItemList(
                    items = items,
                    isEditing = isEditing,
                    mealType = mealType,
                    dateKey = dateKey,
                    onMealTypeSelect = viewModel::setMealType,
                    onGramsChange = viewModel::setGrams,
                    onRemove = viewModel::remove,
                    // 必须走 openManual（内部先 cancelResolve）：直接用裸的 onAddManual
                    // 会把「等待替换某条」的标记留在会话里，下一次普通新增就会误替换掉上一条。
                    onAddManual = openManual,
                    onResolve = { index -> viewModel.beginResolve(index, onAddManual) },
                    onSaveToLibrary = { index -> viewModel.saveToLibrary(index) { actionMessage = it } },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InlineWarning(message: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CalorieColors.PrimaryContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "手动添加",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onAdd() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CalorieColors.PrimaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
            modifier = Modifier.clickable { onDismiss() },
        )
    }
}

@Composable
private fun ItemList(
    items: List<DraftItem>,
    isEditing: Boolean,
    mealType: MealType,
    dateKey: String,
    onMealTypeSelect: (MealType) -> Unit,
    onGramsChange: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddManual: () -> Unit,
    onResolve: (Int) -> Unit,
    onSaveToLibrary: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    items.isNotEmpty() -> "识别到 ${items.size} 种食物"
                    isEditing -> "这条记录还没有食物"
                    else -> "还没有添加食物"
                },
                style = MaterialTheme.typography.titleMedium,
                color = CalorieColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            MealTypeChip(
                selected = mealType,
                dateLabel = if (dateKey == com.calorietracker.app.util.DateUtil.todayKey()) null else dateKey,
                onSelect = onMealTypeSelect,
            )
        }
        Spacer(Modifier.height(12.dp))

        items.forEachIndexed { index, item ->
            DraftItemCard(
                item = item,
                onGramsChange = { onGramsChange(index, it) },
                onRemove = { onRemove(index) },
                onResolve = { onResolve(index) },
                onSaveToLibrary = { onSaveToLibrary(index) },
            )
            Spacer(Modifier.height(12.dp))
        }

        DashedAddCard(text = "+ 手动添加食物", onClick = onAddManual)
    }
}

@Composable
private fun ResultTopBar(
    title: String,
    rightText: String,
    onBack: () -> Unit,
    onRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.headlineMedium,
            color = CalorieColors.TextPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onBack() }
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextPrimary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = rightText,
            style = MaterialTheme.typography.bodyMedium,
            color = CalorieColors.TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onRight() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PhotoBanner(model: Any, badge: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Divider),
    ) {
        AsyncImage(
            model = model,
            contentDescription = "刚拍的照片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xCC1C1F23))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun MealTypeChip(
    selected: MealType,
    dateLabel: String?,
    onSelect: (MealType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CalorieColors.PrimaryContainer)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateLabel?.let { "$it · ${selected.label}" } ?: selected.label,
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.Primary,
            )
            Spacer(Modifier.size(4.dp))
            Text(text = "⌄", style = MaterialTheme.typography.labelSmall, color = CalorieColors.Primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MealType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DraftItemCard(
    item: DraftItem,
    onGramsChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onResolve: () -> Unit,
    onSaveToLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CalorieColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🥗", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // 「营养待补充」的项**绝不能显示成 0 千卡**：
                    // 那个 0 是本地占位值，不是模型给的结果。写成「0 千卡」等于替用户下了
                    // 「这道菜没有热量」的结论 —— 用户看到的就是一个假的 0。
                    // 待补充就如实写「待补充」，与下面那行「还没算进合计」保持一致。
                    text = if (item.needsNutrition) {
                        "待补充"
                    } else {
                        "${NutritionMath.formatKcal(item.kcal)} 千卡"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.needsNutrition) {
                        CalorieColors.TextSecondary
                    } else {
                        CalorieColors.TextPrimary
                    },
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "删除",
                        tint = CalorieColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append("约 ${item.grams}g")
                        item.source?.let { append(" · $it") }
                        if (item.userEdited) append(" · 已调整")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.aiEstimated) CalorieColors.Accent else CalorieColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                GramsStepper(value = item.grams, onValueChange = onGramsChange)
            }

            // 数据来源不同，处理方式也不同：AI 估算的可以存进库，缺数据的必须补上
            if (item.needsNutrition) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CalorieColors.PrimaryContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AI 给不出可靠的营养值，这项还没算进合计",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "从食物库补上",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onResolve() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else if (item.aiEstimated) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CalorieColors.PrimaryContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "营养值为 AI 估算（非成分表）",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "存进我的食物",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSaveToLibrary() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSummaryBar(
    total: Double,
    busy: Boolean = false,
    pending: Int = 0,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CalorieColors.Surface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                // 有「营养待补充」的项时，把按钮为什么点不动写在它左边 ——
                // 光置灰不给理由是让人干着急（合计里那几项也没算进去）
                text = if (pending > 0) "还有 $pending 项待补充" else "本餐合计",
                style = MaterialTheme.typography.labelSmall,
                color = if (pending > 0) CalorieColors.Accent else CalorieColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = NutritionMath.formatKcal(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = CalorieColors.TextPrimary,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "千卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryActionButton(
            text = when {
                busy -> "保存中…"
                pending > 0 -> "先补上营养值"
                else -> "确认记录"
            },
            onClick = onConfirm,
            enabled = !busy && pending == 0,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .heightIn(min = 52.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun ConfirmScreenPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            ResultTopBar(title = "识别结果", rightText = "重新拍", onBack = {}, onRight = {})
            Spacer(Modifier.height(16.dp))
            PhotoBanner(model = "", badge = "刚刚拍摄")
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "识别到 3 种食物",
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                MealTypeChip(selected = MealType.LUNCH, dateLabel = null, onSelect = {})
            }
            Spacer(Modifier.height(12.dp))
            PREVIEW_ITEMS.forEach { item ->
                DraftItemCard(
                    item = item,
                    onGramsChange = {},
                    onRemove = {},
                    onResolve = {},
                    onSaveToLibrary = {},
                )
                Spacer(Modifier.height(12.dp))
            }
            DashedAddCard(text = "+ 手动添加食物", onClick = {})
            Spacer(Modifier.height(20.dp))
            BottomSummaryBar(total = PREVIEW_ITEMS.sumOf { it.kcal }, onConfirm = {})
        }
    }
}

private val PREVIEW_ITEMS = listOf(
    DraftItem(
        foodName = "番茄炒蛋",
        grams = 180,
        nutrition = NutritionSummary(186.0, 8.2, 11.0, 12.4),
        source = "中国食物成分表 第6版",
    ),
    DraftItem(
        foodName = "白米饭",
        grams = 200,
        nutrition = NutritionSummary(232.0, 51.8, 5.2, 0.6),
        source = "中国食物成分表 第6版",
    ),
    DraftItem(
        foodName = "炒青菜",
        grams = 150,
        nutrition = NutritionSummary(78.0, 4.4, 3.3, 5.8),
        source = "中国食物成分表 第6版",
    ),
)
