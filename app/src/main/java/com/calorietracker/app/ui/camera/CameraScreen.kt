package com.calorietracker.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraUseCasePreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.domain.session.RecognitionState
import com.calorietracker.app.ui.theme.CalorieColors
import com.calorietracker.app.ui.theme.CalorieTrackerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onRecognitionFinished: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenAiSettings: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val permission = rememberPermissionState(Manifest.permission.CAMERA)
    val recognitionState by viewModel.recognitionState.collectAsStateWithLifecycle()
    var requestedOnce by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var focusEnabled by remember { mutableStateOf(true) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CameraEvent.GoToConfirm -> onRecognitionFinished()
                CameraEvent.GoToManual -> onManualEntry()
            }
        }
    }

    // 首次进入直接申请相机权限
    LaunchedEffect(Unit) {
        if (!permission.status.isGranted) {
            requestedOnce = true
            permission.launchPermissionRequest()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onPickedFromGallery(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalorieColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        TopBar(
            torchOn = torchOn,
            onClose = onClose,
            onToggleTorch = { torchOn = !torchOn },
        )
        Spacer(Modifier.height(16.dp))

        if (!permission.status.isGranted) {
            CameraPermissionPage(
                showSettings = requestedOnce && !permission.status.shouldShowRationale,
                onRequest = { permission.launchPermissionRequest() },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onManualEntry = { viewModel.gotoManual() },
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF11161B)),
            ) {
                CameraPreview(
                    torchOn = torchOn,
                    focusEnabled = focusEnabled,
                    onImageCaptureReady = { imageCapture = it },
                    onCameraUnavailable = {
                        // 相机被占用 / 启动失败时，绝不能留一个点不动的快门给用户
                        imageCapture = null
                        viewModel.reportCaptureProblem("相机启动失败，可能被其他应用占用")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ViewfinderOverlay(modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(20.dp))
            StatusPanel(
                state = recognitionState,
                onRetry = { viewModel.retry() },
                onManualEntry = { viewModel.gotoManual() },
                onOpenAiSettings = onOpenAiSettings,
            )
            Spacer(Modifier.height(20.dp))

            BottomControls(
                focusEnabled = focusEnabled,
                // 识别中禁用快门：否则用户连点会打断正在跑的那一轮，白等一整轮
                shutterEnabled = recognitionState !is RecognitionState.Recognizing,
                onToggleFocus = { focusEnabled = !focusEnabled },
                onShutter = {
                    val capture = imageCapture
                    if (capture == null) {
                        viewModel.reportCaptureProblem("相机还没准备好，可以重试或直接手动录入")
                    } else {
                        // 代次随每次拍照递增；回调带回同一个代次才生效
                        val generation = viewModel.beginCapture()
                        val file = viewModel.newCaptureFile()
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        capture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                    viewModel.onImageSaved(generation, viewModel.uriFor(file))
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    viewModel.onCaptureFailed(generation, "拍照失败，请重试")
                                }
                            },
                        )
                    }
                },
                onGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TopBar(torchOn: Boolean, onClose: () -> Unit, onToggleTorch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(onClick = onClose) {
            androidx.compose.material3.Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = CalorieColors.TextPrimary,
            )
        }
        Text(
            text = "拍照识别",
            style = MaterialTheme.typography.titleMedium,
            color = CalorieColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconCircle(onClick = onToggleTorch) {
            androidx.compose.material3.Icon(
                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "闪光灯",
                tint = CalorieColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun IconCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun CameraPermissionPage(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要相机权限", style = MaterialTheme.typography.headlineMedium, color = CalorieColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "拍照识别需要用到相机。你也可以先手动录入，随时再来开启。",
            style = MaterialTheme.typography.bodyMedium,
            color = CalorieColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(CalorieColors.Primary)
                .clickable { if (showSettings) onOpenSettings() else onRequest() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (showSettings) "去系统设置开启" else "允许使用相机",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "手动录入",
            style = MaterialTheme.typography.bodyMedium,
            color = CalorieColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onManualEntry() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StatusPanel(
    state: RecognitionState,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CalorieColors.Surface)
            .padding(vertical = 20.dp, horizontal = 20.dp),
    ) {
        when (state) {
            is RecognitionState.Failure -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "识别失败了",
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedPill(text = "重试", onClick = onRetry, modifier = Modifier.weight(1f))
                    FilledPill(text = "手动录入", onClick = onManualEntry, modifier = Modifier.weight(1f))
                }
                // Key 没填 / 填错时，直接把用户送到设置页，别让人在这里瞎试
                if (state.needsAiConfig) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "去 AI 设置 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalorieColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenAiSettings() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            else -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (state is RecognitionState.Recognizing) "正在识别…" else "对准食物后按快门",
                    style = MaterialTheme.typography.titleMedium,
                    color = CalorieColors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "请将食物完整置于框内",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalorieColors.TextSecondary,
                )
                if (state is RecognitionState.Recognizing) {
                    Spacer(Modifier.height(14.dp))
                    BouncingDots()
                    ElapsedHint()
                }
            }
        }
    }
}

/**
 * 识别期间显示已用秒数。
 *
 * 模型正常 3~10 秒出结果；把已用秒数显示出来，用户能分清「在等模型」还是「App 卡住了」，
 * 等待感会明显好很多。配合客户端 60 秒读超时 / 75 秒整通超时，
 * 最坏情况也一定能等到一个明确结果，而不是无限转圈。
 */
@Composable
private fun ElapsedHint() {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (seconds < 10) "已用 ${seconds}s · 通常 3~10 秒出结果"
        else "已用 ${seconds}s · 模型响应偏慢，可换更快的模型",
        style = MaterialTheme.typography.labelSmall,
        color = CalorieColors.TextSecondary,
    )
}

@Composable
private fun OutlinedPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, CalorieColors.Divider, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CalorieColors.TextPrimary)
    }
}

@Composable
private fun FilledPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CalorieColors.Primary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/** 三个跳动的圆点 */
@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CalorieColors.Primary.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun BottomControls(
    focusEnabled: Boolean,
    /** 识别进行中置 false：连点快门会打断/白等一整轮识别 */
    shutterEnabled: Boolean = true,
    onToggleFocus: () -> Unit,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CalorieColors.Surface)
                .clickable { onGallery() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = "从相册选择",
                tint = CalorieColors.TextSecondary,
            )
        }

        // 大圆快门。
        // 识别中要禁用：以前可以连点，虽然代次守卫能防止重复写库，
        // 但会打断正在跑的那一轮、让用户白等一整轮识别。
        // onClickLabel 是给 TalkBack 的：这个快门是个纯色圆、没有文字/图标，
        // 不给标签的话读屏用户根本不知道它是什么。
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(CalorieColors.Surface)
                .clickable(enabled = shutterEnabled, onClickLabel = "拍照") { onShutter() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        if (shutterEnabled) CalorieColors.Primary else CalorieColors.Divider,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CalorieColors.Surface)
                .clickable { onToggleFocus() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (focusEnabled) Icons.Outlined.CenterFocusStrong
                else Icons.Outlined.CenterFocusWeak,
                contentDescription = "对焦",
                tint = if (focusEnabled) CalorieColors.Primary else CalorieColors.TextSecondary,
            )
        }
    }
}

/** 四角取景框 + 上下扫动的扫描光带 */
@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanLine",
    )

    Canvas(modifier = modifier.padding(28.dp)) {
        val corner = 34.dp.toPx()
        val width = 3.dp.toPx()
        val w = size.width
        val h = size.height
        val color = CalorieColors.Primary

        // 四个 L 形角
        listOf(
            Triple(Offset(0f, 0f), 1f, 1f),
            Triple(Offset(w, 0f), -1f, 1f),
            Triple(Offset(0f, h), 1f, -1f),
            Triple(Offset(w, h), -1f, -1f),
        ).forEach { (origin, dx, dy) ->
            drawLine(color, origin, Offset(origin.x + corner * dx, origin.y), width, StrokeCap.Round)
            drawLine(color, origin, Offset(origin.x, origin.y + corner * dy), width, StrokeCap.Round)
        }

        // 扫描光带
        val y = h * progress
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.85f), Color.Transparent),
                startY = y - 40f,
                endY = y + 40f,
            ),
            topLeft = Offset(0f, y - 3f),
            size = Size(w, 6f),
            cornerRadius = CornerRadius(3f, 3f),
        )
    }
}

@Composable
private fun CameraPreview(
    torchOn: Boolean,
    focusEnabled: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var bound by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val resolved = context.awaitCameraProvider() ?: return@LaunchedEffect
        provider = resolved
        val preview = CameraUseCasePreview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val bindResult = runCatching {
            resolved.unbindAll()
            resolved.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }
        bindResult.onSuccess { boundCamera ->
            camera = boundCamera
            capture = imageCapture
            bound = true
            onImageCaptureReady(imageCapture)
        }.onFailure {
            bound = false
            onCameraUnavailable()
        }
    }

    // 离开页面时**显式**解绑。
    //
    // bindToLifecycle 理论上会随 lifecycleOwner 销毁自动解绑，但那依赖
    // 「lifecycleOwner 一定是 NavBackStackEntry」这个前提：一旦本页将来被 Activity 直接承载、
    // 或 navigation 的覆盖失效，相机与预览就会一直被占着（相机是独占资源，
    // 不解绑还会导致下次进页面起不来）。这里不依赖隐式行为。
    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { provider?.unbindAll() }
            provider = null
            capture = null
            camera = null
            bound = false
        }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // 点按对焦
    DisposableEffect(focusEnabled, camera) {
        val activeCamera = camera
        previewView.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && focusEnabled && activeCamera != null) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
                activeCamera.cameraControl.startFocusAndMetering(action)
                true
            } else {
                false
            }
        }
        onDispose { previewView.setOnTouchListener(null) }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    if (!bound) {
        // 相机没起来时给个可见提示，避免一片死黑让人以为卡住
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "正在启动相机…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

/** ProcessCameraProvider 的 ListenableFuture 转成挂起函数，省掉额外依赖 */
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider? =
    suspendCancellableCoroutine { continuation ->
        // 必须用 applicationContext，不能用 `this`（在 Compose 里通常就是 Activity）：
        // getInstance 只需要一个 Context，而后面的 listener 会一直挂在 future 上，
        // 用 Activity 就等于「等相机就绪期间一直被持有」。
        //
        // 另：Guava 的 ListenableFuture **不支持移除监听器**，所以协程取消时无法摘除回调；
        // 真正的保护是下面每处的 `continuation.isActive` 判断（取消后不再 resume，
        // 监听器随 future 完成即释放）。provider 就绪很快，属短时持有而非泄漏。
        val appContext = applicationContext
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { provider ->
                        if (continuation.isActive) continuation.resume(provider)
                    }
                    .onFailure { error ->
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F9)
@Composable
private fun CameraScreenPreview() {
    CalorieTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CalorieColors.Background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            TopBar(torchOn = false, onClose = {}, onToggleTorch = {})
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF11161B)),
            ) {
                ViewfinderOverlay(modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(20.dp))
            StatusPanel(
                state = RecognitionState.Recognizing,
                onRetry = {},
                onManualEntry = {},
            )
            Spacer(Modifier.height(20.dp))
            BottomControls(
                focusEnabled = false,
                onToggleFocus = {},
                onShutter = {},
                onGallery = {},
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
