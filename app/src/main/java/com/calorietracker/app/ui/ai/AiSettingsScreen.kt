package com.calorietracker.app.ui.ai

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.data.ai.AiCapability
import com.calorietracker.app.data.ai.AiProviderConfig
import com.calorietracker.app.data.ai.AiTestResult
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme

/**
 * AI 设置。
 *
 * 结构照搬 BeeCount 的「能力-服务商」模型：
 * 一个服务商里可以分别填 文本 / 图片 模型，能力是否可用 = 对应模型名是否非空；
 * 上面再有一层「图片识别用哪家」的绑定，换模型不用改识别代码。
 */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val binding by viewModel.binding.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedId.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // 头部固定、只有内容滚动：设置页很长，返回按钮不能跟着内容滚走
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalorieColors.Background)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                text = "AI 设置",
                style = MaterialTheme.typography.titleMedium,
                color = CalorieColors.TextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
        BindingCard(providers = providers, viewModel = viewModel)

        Spacer(Modifier.height(16.dp))
        Text(
            text = "填一个服务商的 API Key 就能用真 AI 识别了。Key 只存在本机，识别时直连该服务商。",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))

        providers.forEach { provider ->
            ProviderCard(
                provider = provider,
                expanded = provider.id == expandedId,
                draft = if (provider.id == expandedId) draft else null,
                tests = tests,
                testing = testing,
                viewModel = viewModel,
            )
            Spacer(Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(CalorieColors.Surface)
                .clickable { viewModel.addCustomProvider() },
            contentAlignment = Alignment.Center,
        ) {
            Text("+ 新增服务商（OpenAI 兼容）", style = MaterialTheme.typography.bodyMedium, color = CalorieColors.Primary)
        }

        if (message != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = message!!,
                style = MaterialTheme.typography.labelSmall,
                color = CalorieColors.Primary,
                modifier = Modifier.clickable { viewModel.consumeMessage() },
            )
        }
        Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BindingCard(providers: List<AiProviderConfig>, viewModel: AiSettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface)
            .padding(20.dp),
    ) {
        Text("识别能力分配", style = MaterialTheme.typography.titleMedium, color = CalorieColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "拍照识别走「图片」；一句话记一餐走「文本」。可以分别指定不同的服务商与模型。",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
        Spacer(Modifier.height(14.dp))

        AiCapability.entries.forEach { capability ->
            val effective = viewModel.effectiveProvider(capability)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = capability.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalorieColors.TextPrimary,
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .heightIn(min = 20.dp),
                )
                val label = when {
                    effective == null -> "未配置"
                    effective.modelFor(capability).isBlank() -> "${effective.name}（没填模型名）"
                    else -> "${effective.name} · ${effective.modelFor(capability)}"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (effective == null) CalorieColors.Accent else CalorieColors.Primary,
                    modifier = Modifier.weight(1f),
                )
                if (effective == null) {
                    Text(
                        text = "不可用",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: AiProviderConfig,
    expanded: Boolean,
    draft: AiProviderConfig?,
    tests: Map<AiCapability, AiTestResult>,
    testing: Set<AiCapability>,
    viewModel: AiSettingsViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (expanded) viewModel.collapse() else viewModel.expand(provider.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = CalorieColors.TextPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    StatusChip(provider)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(provider.baseUrl.ifBlank { "未填接口地址" })
                        val models = AiCapability.entries
                            .filter { provider.modelFor(it).isNotBlank() }
                            .joinToString(" / ") { provider.modelFor(it) }
                        if (models.isNotBlank()) append("\n$models")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
            }
            Text(
                text = if (expanded) "⌃" else "⌄",
                style = MaterialTheme.typography.titleMedium,
                color = CalorieColors.TextSecondary,
            )
        }

        if (expanded && draft != null) {
            Spacer(Modifier.height(16.dp))
            Editor(
                draft = draft,
                tests = tests,
                testing = testing,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun StatusChip(provider: AiProviderConfig) {
    val ready = AiCapability.entries.any { provider.usableFor(it) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (ready) CalorieColors.PrimaryContainer else CalorieColors.Divider)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (ready) "可用" else "待配置",
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) CalorieColors.Primary else CalorieColors.TextSecondary,
        )
    }
}

@Composable
private fun Editor(
    draft: AiProviderConfig,
    tests: Map<AiCapability, AiTestResult>,
    testing: Set<AiCapability>,
    viewModel: AiSettingsViewModel,
) {
    val uriHandler = LocalUriHandler.current

    AiField(
        label = "名称",
        value = draft.name,
        placeholder = "给自己看的备注",
        onChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
    )
    AiField(
        label = "接口地址（OpenAI 兼容根地址，结尾不要带 /）",
        value = draft.baseUrl,
        placeholder = "https://api.deepseek.com/v1",
        onChange = { value -> viewModel.updateDraft { it.copy(baseUrl = value.trim()) } },
    )
    AiField(
        label = "API Key",
        value = draft.apiKey,
        placeholder = "sk-...",
        secret = true,
        onChange = { value -> viewModel.updateDraft { it.copy(apiKey = value.trim()) } },
    )

    Spacer(Modifier.height(6.dp))
    AiCapability.entries.forEach { capability ->
        ModelRow(
            capability = capability,
            model = draft.modelFor(capability),
            test = tests[capability],
            isTesting = capability in testing,
            bound = viewModel.effectiveProvider(capability)?.id == draft.id,
            onModelChange = { value ->
                viewModel.updateDraft { config ->
                    when (capability) {
                        AiCapability.TEXT -> config.copy(textModel = value.trim())
                        AiCapability.VISION -> config.copy(visionModel = value.trim())
                    }
                }
            },
            onTest = { viewModel.test(capability) },
            onBind = { viewModel.bind(capability, draft.id) },
        )
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionPill(
            text = if (testing.isNotEmpty()) "测试中…" else "一键测试全部",
            filled = false,
            enabled = testing.isEmpty(),
            onClick = { viewModel.testAll() },
            modifier = Modifier.weight(1f),
        )
        ActionPill(
            text = "保存",
            filled = true,
            enabled = true,
            onClick = { viewModel.saveDraft() },
            modifier = Modifier.weight(1f),
        )
    }

    if (draft.consoleUrl.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "去 ${draft.name} 申请 API Key ›",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.Primary,
            modifier = Modifier.clickable { uriHandler.openUri(draft.consoleUrl) },
        )
    }
    if (draft.hint.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = draft.hint,
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.TextSecondary,
        )
    }
    if (!draft.isBuiltIn) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "删除这个服务商",
            style = MaterialTheme.typography.labelSmall,
            color = CalorieColors.Accent,
            modifier = Modifier.clickable { viewModel.delete(draft.id) },
        )
    }
}

@Composable
private fun ModelRow(
    capability: AiCapability,
    model: String,
    test: AiTestResult?,
    isTesting: Boolean,
    bound: Boolean,
    onModelChange: (String) -> Unit,
    onTest: () -> Unit,
    onBind: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        AiField(
            label = "${capability.label}模型" + if (bound) "（当前使用中）" else "",
            value = model,
            placeholder = "模型名，留空表示不使用该能力",
            onChange = onModelChange,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill(
                text = if (isTesting) "测试中…" else "测试连接",
                filled = false,
                enabled = !isTesting && model.isNotBlank(),
                onClick = onTest,
                modifier = Modifier.weight(1f),
            )
            ActionPill(
                text = if (bound) "已用于${capability.label}" else "用于${capability.label}",
                filled = bound,
                enabled = !bound && model.isNotBlank(),
                onClick = onBind,
                modifier = Modifier.weight(1f),
            )
        }
        if (test != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (test.ok) "✓ ${test.message}" else "✗ ${test.message}",
                style = MaterialTheme.typography.labelSmall,
                color = if (test.ok) CalorieColors.Primary else CalorieColors.Accent,
            )
        }
    }
}

@Composable
private fun AiField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    secret: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CalorieColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CalorieColors.TextSecondary)
            },
            visualTransformation = if (secret && !visible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (secret) {
                {
                    Text(
                        text = if (visible) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.Primary,
                        modifier = Modifier
                            .clickable { visible = !visible }
                            .padding(end = 12.dp),
                    )
                }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CalorieColors.Background,
                unfocusedContainerColor = CalorieColors.Background,
                focusedBorderColor = CalorieColors.Primary,
                unfocusedBorderColor = CalorieColors.Divider,
                focusedTextColor = CalorieColors.TextPrimary,
                unfocusedTextColor = CalorieColors.TextPrimary,
                cursorColor = CalorieColors.Primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionPill(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(
                when {
                    !enabled -> CalorieColors.Divider
                    filled -> CalorieColors.Primary
                    else -> CalorieColors.PrimaryContainer
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> CalorieColors.TextSecondary
                filled -> Color.White
                else -> CalorieColors.Primary
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun AiSettingsPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(20.dp),
        ) {
            Text("AI 设置", style = MaterialTheme.typography.headlineMedium, color = CalorieColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CalorieColors.Surface)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("智谱 GLM", style = MaterialTheme.typography.titleMedium, color = CalorieColors.TextPrimary)
                    Spacer(Modifier.size(8.dp))
                    StatusChip(
                        AiProviderConfig(
                            id = "zhipu",
                            name = "智谱 GLM",
                            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                            apiKey = "sk-test",
                            textModel = "glm-4-flash",
                            visionModel = "glm-4v-flash",
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                AiField(
                    label = "API Key",
                    value = "sk-1234567890abcdef",
                    placeholder = "sk-...",
                    secret = true,
                    onChange = {},
                )
                Spacer(Modifier.height(8.dp))
                ModelRow(
                    capability = AiCapability.VISION,
                    model = "glm-4v-flash",
                    test = AiTestResult(AiCapability.VISION, true, "连接正常"),
                    isTesting = false,
                    bound = true,
                    onModelChange = {},
                    onTest = {},
                    onBind = {},
                )
            }
        }
    }
}
