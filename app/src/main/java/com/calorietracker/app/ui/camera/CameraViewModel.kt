package com.calorietracker.app.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.ai.AiCapability
import com.calorietracker.app.data.ai.AiConfigStore
import com.calorietracker.app.data.ai.AiException
import com.calorietracker.app.data.ai.AiFoodExtractor
import com.calorietracker.app.data.local.PhotoStorage
import com.calorietracker.app.data.repository.FoodRepository
import com.calorietracker.app.domain.model.DraftItem
import com.calorietracker.app.domain.model.MealType
import com.calorietracker.app.domain.recognition.FoodRecognitionService
import com.calorietracker.app.domain.recognition.RecognitionException
import com.calorietracker.app.domain.session.MealDraftSession
import com.calorietracker.app.domain.session.RecognitionState
import com.calorietracker.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CameraEvent {
    data object GoToConfirm : CameraEvent
    data object GoToManual : CameraEvent
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val recognitionService: FoodRecognitionService,
    private val foodRepository: FoodRepository,
    private val photoStorage: PhotoStorage,
    private val session: MealDraftSession,
    private val aiExtractor: AiFoodExtractor,
    private val configStore: AiConfigStore,
) : ViewModel() {

    val recognitionState: StateFlow<RecognitionState> = session.state

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    private var captureTimeoutJob: Job? = null

    /** 拍照代次：递增一次代表「新一轮拍照」，用于丢弃上一轮的迟到回调 */
    private var captureGeneration = 0

    /**
     * 已经处理过的代次（**防御性**）。
     *
     * 代次守卫只挡「上一轮的迟到回调」，挡不住「同一代次被回调两次」。
     * 本轮实测中出现的那两次识别，最终查明来自**两次不同的相册选择**（两个不同 URI），
     * 不是相机重复回调 —— 所以这里**没有复现证据**。但重复回调一旦发生，
     * 代价是用户白等一整轮识别，所以保留这道关卡。
     */
    private var handledGeneration = -1

    /** 当前在跑的识别任务：开新一轮之前先取消它 */
    private var recognizeJob: Job? = null

    /**
     * 识别代次：每发起一轮识别递增。
     *
     * 相机回调本来就有代次守卫（[captureGeneration]），但**识别协程**此前没有：
     * 上一轮识别的结果可能在新一轮（例如用户改用相册选图）开始之后才返回，
     * 于是「后写覆盖先写」，还会多发一次结果页导航。这里给识别补上同样的关卡。
     */
    private var recognizeGeneration = 0

    /** 本次拍摄的照片路径（写库时存进 MealEntry.photoPath） */
    val photoUri: StateFlow<Uri?> = session.photoUri

    /** CameraX 的 ImageCapture 只接受 File 输出 */
    fun newCaptureFile(): java.io.File = photoStorage.newPhotoFile()

    fun uriFor(file: java.io.File): Uri = photoStorage.toUri(file)

    fun setMealType(type: MealType) = session.setMealType(type)

    /**
     * 按下快门，开始计时，返回本次拍照的**代次**。
     *
     * 相机被占用或预览管线异常时 ImageCapture 可能一个回调都不给，
     * 用户会对着「拍照中」无限等，所以超时后主动给「重试 / 手动录入」。
     *
     * 代次是给回调用的：相机的回调可能迟到（实测有 10 秒后才回来的），
     * 那时用户可能已经改用相册选图并识别成功。代次对不上就丢弃，
     * 避免一次迟到的失败回调把已经出好的结果页冲掉。
     */
    /**
     * 拍照链路上的调试日志（仅 DEBUG）。
     *
     * 为什么值得留：这条链上任何一次**重复触发**都会让用户白等一整轮识别，
     * 而事后看日志只能看到识别结果、看不到是谁触发的。
     * 每个入口各记一笔，排查时 `adb logcat | grep CameraCapture` 就能一眼定位
     * （本轮定位「一次操作跑了两轮识别」就是靠它）。
     */
    private fun captureLog(message: String) {
        if (com.calorietracker.app.BuildConfig.DEBUG) {
            android.util.Log.d("CameraCapture", message)
        }
    }

    fun beginCapture(): Int {
        captureGeneration++
        val generation = captureGeneration
        captureLog("beginCapture gen=$generation")
        captureTimeoutJob?.cancel()
        captureTimeoutJob = viewModelScope.launch {
            delay(CAPTURE_TIMEOUT_MS)
            if (generation == captureGeneration) {
                captureLog("captureTimeout gen=$generation")
                session.onRecognitionFailed("相机没有响应，请重试或手动录入")
            }
        }
        return generation
    }

    /**
     * 相机成功回图。
     *
     * 两道关：① 代次对不上 = 上一轮的迟到回调，丢弃（**有实测依据**：相机回调曾迟到 10 秒）；
     * ② 代次相同但已处理过 = 同一次回调被重复投递，丢弃（**防御性**，见 [handledGeneration]）。
     */
    fun onImageSaved(generation: Int, uri: Uri) {
        captureLog("onImageSaved gen=$generation cur=$captureGeneration handled=$handledGeneration")
        if (generation != captureGeneration) return
        if (generation == handledGeneration) return
        handledGeneration = generation
        captureTimeoutJob?.cancel()
        startRecognitionFlow(uri)
    }

    /** 拍照失败（存储、对焦、硬件异常）。同样按代次只处理一次。 */
    fun onCaptureFailed(generation: Int, message: String) {
        captureLog("onCaptureFailed gen=$generation cur=$captureGeneration handled=$handledGeneration msg=$message")
        if (generation != captureGeneration) return
        if (generation == handledGeneration) return
        handledGeneration = generation
        captureTimeoutJob?.cancel()
        session.onRecognitionFailed(message)
    }

    /** 用户主动点快门但相机没就绪：直接生效，并作废在途的旧回调 */
    fun reportCaptureProblem(message: String) {
        captureGeneration++
        captureLog("reportCaptureProblem cur=$captureGeneration msg=$message")
        captureTimeoutJob?.cancel()
        session.onRecognitionFailed(message)
    }

    /** 相册选图：先复制进私有目录再走识别，避免授权过期后图片打不开 */
    fun onPickedFromGallery(uri: Uri) {
        // 作废仍在途的拍照回调，否则它回来时会把这次选图的结果冲掉
        captureGeneration++
        captureLog("onPickedFromGallery cur=$captureGeneration uri=$uri")
        captureTimeoutJob?.cancel()
        viewModelScope.launch {
            val copied = photoStorage.importFrom(uri)
            val target = copied?.let { photoStorage.toUri(it) } ?: uri
            startRecognitionFlow(target)
        }
    }

    fun retry() {
        captureLog("retry lastPhoto=${_lastPhotoUri.value}")
        val uri = _lastPhotoUri.value ?: run {
            _events.trySend(CameraEvent.GoToManual)
            return
        }
        session.onRecognizing()
        recognize(uri)
    }

    /**
     * 拍照/选图完成后统一走这里。
     *
     * 餐次按**当前时间**推断（和「一句话记一餐」一致）：下午拍的照片默认记成午餐。
     * 之前是把会话里上一次的餐次原样传回来 —— 默认恒为「加餐」，
     * 而且用户刚编辑过一条「早餐」记录再拍照，新照片会被默认标成早餐。
     */
    private fun startRecognitionFlow(uri: Uri) {
        _lastPhotoUri.value = uri
        session.setDateKey(DateUtil.todayKey())
        session.startPhotoFlow(uri, MealType.byHour(DateUtil.hourOfDay()))
        recognize(uri)
    }

    fun gotoManual() {
        session.startManualFlow(MealType.byHour(DateUtil.hourOfDay()))
        _events.trySend(CameraEvent.GoToManual)
    }

    /**
     * 识别。两条路径：
     *
     * 1. **配了 AI** → 视觉模型看图给名称/份量/每 100g 值；
     *    名称能落到本地权威库的用库里的值，落到不了才用 AI 估算并明确标注。
     * 2. **没配 AI** → 回落到离线模拟识别（从本地库随机抽样），
     *    并在结果页顶部明确提示「这是演示数据」，绝不让人以为是真识别。
     */
    private fun recognize(uri: Uri) {
        // 同一时刻只允许一轮识别在跑：先取消上一轮。
        // 不取消的话两轮并行，「谁后返回谁覆盖结果」，还会各发一次 GoToConfirm
        // 把结果页压两层。
        recognizeJob?.cancel()
        recognizeGeneration++
        val generation = recognizeGeneration
        recognizeJob = viewModelScope.launch {
            captureLog("recognize start gen=$generation uri=$uri")
            val aiReady = configStore.isReady(AiCapability.VISION)
            try {
                val drafts = if (aiReady) {
                    session.setNotice(null)
                    aiExtractor.extractFromImage(uri)
                } else {
                    session.setNotice("当前是离线演示识别（未配置 AI），去「我的 → AI 设置」填入视觉模型后即为真实识别")
                    mockRecognize(uri)
                }

                // 迟到的一轮直接丢弃：不许它改写会话状态，也不许它再发一次导航
                if (generation != recognizeGeneration) return@launch
                if (drafts.isEmpty()) {
                    session.onRecognitionFailed("没有识别到食物")
                    return@launch
                }
                session.onRecognized(drafts)
                _events.trySend(CameraEvent.GoToConfirm)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 取消 ≠ 失败，必须原样抛出。
                // 否则「用户离开页面 / 又开了新一轮」会被写成失败态，而 session 是单例，
                // 那条失败横幅会挂到下一轮记录上（catch (Exception) 会把取消一起吞掉）。
                throw e
            } catch (e: AiException) {
                if (generation != recognizeGeneration) return@launch
                session.onRecognitionFailed(
                    message = e.userMessage,
                    needsAiConfig = e.kind == AiException.Kind.NOT_CONFIGURED ||
                        e.kind == AiException.Kind.UNAUTHORIZED ||
                        e.kind == AiException.Kind.NOT_FOUND,
                )
            } catch (e: RecognitionException.Timeout) {
                if (generation != recognizeGeneration) return@launch
                session.onRecognitionFailed("识别超时了，请重试")
            } catch (e: RecognitionException.Offline) {
                if (generation != recognizeGeneration) return@launch
                session.onRecognitionFailed("网络不可用，先手动录入吧")
            } catch (e: RecognitionException.ServiceError) {
                if (generation != recognizeGeneration) return@launch
                session.onRecognitionFailed(e.message ?: "识别失败了")
            } catch (e: Exception) {
                if (generation != recognizeGeneration) return@launch
                session.onRecognitionFailed("识别失败了")
            }
        }
    }

    /** 离线演示：从本地食物库随机抽 2~4 条，热量仍然是真的，只是「识别」是假的 */
    private suspend fun mockRecognize(uri: Uri): List<DraftItem> {
        val recognized = recognitionService.recognize(uri)
        return recognized.mapNotNull { item ->
            val food = foodRepository.resolveByName(item.name) ?: return@mapNotNull null
            DraftItem(
                foodId = food.id,
                foodName = food.name,
                grams = item.estimatedGrams,
                nutrition = foodRepository.toSummary(food, item.estimatedGrams),
                userEdited = false,
                source = food.source,
                confidence = item.confidence,
            )
        }
    }

    private companion object {
        /** 按快门后等待相机回图的上限 */
        const val CAPTURE_TIMEOUT_MS = 8_000L
    }
}
