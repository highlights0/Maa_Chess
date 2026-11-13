package com.aliothmoon.maameow.overlay

import android.app.Application
import com.aliothmoon.maameow.R
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.overlay.border.BorderOverlayManager
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import com.aliothmoon.maameow.presentation.components.LocalPagerPreloadState
import com.aliothmoon.maameow.presentation.components.PagerPreloadState
import com.aliothmoon.maameow.presentation.view.xiangqi.XiangqiForegroundPanel
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.service.AccessibilityHelperService
import com.aliothmoon.maameow.theme.MaaMeowTheme
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.UiScale
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxDisplayMode
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import com.petterp.floatingx.listener.IKeyBackListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.domain.xiangqi.XiangqiMove
import com.aliothmoon.maameow.domain.xiangqi.xiangqiPieceName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class OverlayController(
    private val context: Application,
    val borderOverlayManager: BorderOverlayManager,
    private val fwViewModelOwner: OverlayViewModelOwner,
    private val compositionService: MaaCompositionService,
    private val appSettings: AppSettingsManager
) {

    companion object {
        private const val MAIN_PANEL_TAG = "MP_TAG"
        private const val FLOAT_BALL_TAG = "FB_TAG"
        private const val ENABLE_LOG = false

        /** 窗口撤掉到合成进屏幕、再送进截图镜像要几帧，实测主线程忙时约 150ms */
        private const val PANEL_CLEAR_SETTLE_MS = 300L
    }

    private val _isLocked = MutableStateFlow(true)
    private val _isActive = MutableStateFlow(false)
    private val xiangqiViewModel = MutableStateFlow<BackgroundTaskViewModel?>(null)
    private val mainPanelVisible = MutableStateFlow(false)
    private val capturingXiangqi = MutableStateFlow(false)
    private val gameHints = XiangqiHintOverlay(context)
    private val autoStatus = XiangqiStatusOverlay(context)
    private val boardCalibration = XiangqiCalibrationOverlay(context)
    fun showXiangqiCalibration(bitmap: android.graphics.Bitmap, initial: com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardCalibration?,
        onSave: (com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardCalibration) -> Unit) = boardCalibration.show(bitmap, initial, onSave)
    private var gameHintJob: Job? = null
    private var autoStatusJob: Job? = null
    fun setXiangqiCaptureActive(active: Boolean) { capturingXiangqi.value = active }
    fun canObserveXiangqi(): Boolean = _isActive.value && !mainPanelVisible.value && !capturingXiangqi.value
    suspend fun <T> withXiangqiStatusCleared(calibration: com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardCalibration, action: suspend () -> T): T {
        while (withContext(Dispatchers.Main) { autoStatus.isDragging }) delay(50)
        val cleared = withContext(Dispatchers.Main) { autoStatus.clearIfOverlapping(calibration) }
        return try {
            if (cleared) delay(150)
            action()
        } finally {
            if (cleared) withContext(NonCancellable + Dispatchers.Main) { autoStatus.restoreAfterCapture() }
        }
    }
    fun showXiangqiAutomationControl() {
        if (!_isActive.value) show(OverlayControlMode.ACCESSIBILITY)
        hideMainPanel()
        hideFloatBall()
    }
    fun attachXiangqiViewModel(viewModel: BackgroundTaskViewModel?) {
        xiangqiViewModel.value = viewModel
        gameHintJob?.cancel()
        autoStatusJob?.cancel()
        gameHints.hide()
        autoStatus.hide()
        if (viewModel != null) autoStatusJob = scope.launch {
            viewModel.xiangqiRecognition.collect { state ->
                if (!state.isAutoPlaying && !state.isStoppingAutomation) {
                    autoStatus.hide()
                } else {
                    fun sideName(side: Char?) = context.getString(when (side) {
                        'w' -> R.string.xiangqi_analysis_red
                        'b' -> R.string.xiangqi_analysis_black
                        else -> R.string.xiangqi_turn_unknown
                    })
                    val phase = when {
                        state.isStoppingAutomation -> context.getString(R.string.xiangqi_auto_stopping)
                        state.isExecutingMove -> state.automationStatus ?: context.getString(R.string.xiangqi_move_running)
                        state.isRecognizing -> state.automationStatus ?: context.getString(R.string.xiangqi_recognition_running)
                        state.isAnalyzing -> context.getString(R.string.xiangqi_analysis_running)
                        else -> state.automationStatus ?: context.getString(R.string.xiangqi_auto_observing)
                    }
                    val currentSide = if (state.isRecognizing || state.inputUnverified) sideName(null)
                        else if (state.defaultRedTurn) context.getString(R.string.xiangqi_assumed_turn_short, sideName(state.detectedSide))
                        else sideName(state.detectedSide)
                    val move = state.analysis?.let { analysis ->
                        val parsed = state.result?.let { XiangqiMove.parse(analysis.bestMove, it.rows, analysis.side) }
                        val piece = parsed?.let { state.result?.rows?.get(it.from.row)?.get(it.from.col) }
                        "${piece?.let(::xiangqiPieceName) ?: ""} ${analysis.bestMove.take(2)}→${analysis.bestMove.drop(2)}"
                    } ?: context.getString(R.string.xiangqi_auto_observing)
                    autoStatus.show(context.getString(R.string.xiangqi_auto_overlay_info, phase,
                        sideName(state.result?.playerSide), currentSide, move)) { viewModel.onStopAutoXiangqi("status-button") }
                }
            }
        }
        if (viewModel != null) gameHintJob = scope.launch {
            val visible = combine(_isActive, mainPanelVisible, capturingXiangqi) { active, panel, capture ->
                active && !panel && !capture
            }
            combine(viewModel.xiangqiRecognition, appSettings.runMode, AccessibilityHelperService.foregroundPackage, visible) { state, mode, pkg, allowed ->
                val result = state.result
                val analysis = state.analysis
                val move = if (allowed && mode == RunMode.FOREGROUND && pkg == Packages["Xiangqi"] &&
                    result?.isComplete == true && analysis != null && state.historyError == null &&
                    !state.inputUnverified && !state.boardChanged && !state.isExecutingMove) {
                    XiangqiMove.parse(analysis.bestMove, result.rows, analysis.side)
                } else null
                result to move
            }.distinctUntilChanged().collect { (result, move) ->
                if (result != null && move != null) gameHints.show(result, move) else gameHints.hide()
            }
        }
    }
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    private var calculatedPanelLayout: Pair<Int, Int>? = null
    private var orientation = context.resources.configuration.orientation

    private val _signal = MutableSharedFlow<MaaExecutionState>(extraBufferCapacity = 1)
    val signal: SharedFlow<MaaExecutionState> = _signal.asSharedFlow()

    private var currentMode: OverlayControlMode = OverlayControlMode.ACCESSIBILITY
    private var maaStateJob: Job? = null

    private val _countdownState = MutableStateFlow<CountdownState>(CountdownState.Idle)
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    var onCountdownClick: (() -> Unit)? = null
    private var tempCountdownListener: (() -> Unit)? = null

    fun setTemporaryCountdownListener(listener: (() -> Unit)?) {
        this.tempCountdownListener = listener
    }

    fun handleCountdownClick() {
        val temp = tempCountdownListener
        if (temp != null) {
            temp.invoke()
        } else {
            onCountdownClick?.invoke()
        }
    }

    fun updateCountdownState(state: CountdownState) {
        _countdownState.value = state
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var panelHiddenAtMs = 0L

    private val callback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val newLayout = calculatePanelLayout(newConfig)
            val changed = newConfig.orientation != orientation || newLayout != calculatedPanelLayout
            calculatedPanelLayout = newLayout
            orientation = newConfig.orientation
            if (changed) {
                applyPanelLayout(newConfig, newLayout)
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() {
        }
    }

    fun setup() {
        context.registerComponentCallbacks(callback)
        scope.launch {
            appSettings.runMode.collect { mode ->
                when (mode) {
                    RunMode.FOREGROUND -> {
                        install()
                        // 触发初始布局计算
                        callback.onConfigurationChanged(context.resources.configuration)
                        startMaaStateObserver()
                    }

                    RunMode.BACKGROUND -> {
                        stopMaaStateObserver()
                        hideAll()
                        uninstall()
                    }
                }
            }
        }
    }

    private fun startMaaStateObserver() {
        if (maaStateJob != null) return
        maaStateJob = scope.launch {
            var previous = compositionService.state.value
            compositionService.state.collect { state ->
                Timber.d("OverlayController: MaaCompositionService state changed: $state")
                onMaaStateChanged(previous, state)
                previous = state
            }
        }
    }

    private fun stopMaaStateObserver() {
        maaStateJob?.cancel()
        maaStateJob = null
    }

    private suspend fun onMaaStateChanged(previous: MaaExecutionState, current: MaaExecutionState) {
        // 仅在 show() 之后才响应运行态切换
        // 前台定时任务不会自动 show：未开控制层时这里直接 return，无悬浮球/音量键
        if (!_isActive.value) return

        val wasActive =
            previous == MaaExecutionState.RUNNING
                    || previous == MaaExecutionState.STARTING
                    || previous == MaaExecutionState.STOPPING
        val isActive =
            current == MaaExecutionState.RUNNING
                    || current == MaaExecutionState.STARTING
                    || current == MaaExecutionState.STOPPING

        when {
            // 进入启动/运行态：隐藏主面板，显示悬浮控件
            // STARTING 就收：core 一 Start 就截首帧，等到 RUNNING 面板还挡着；RUNNING 再收一次，防启动中被点开
            (current == MaaExecutionState.STARTING || current == MaaExecutionState.RUNNING)
                    && previous != current -> {
                hideMainPanel()
                when (currentMode) {
                    OverlayControlMode.FLOAT_BALL -> {
                        showFloatBall()
                        Timber.d("OverlayController: 任务运行中，显示悬浮球")
                    }

                    OverlayControlMode.ACCESSIBILITY -> {
                        borderOverlayManager.show()
                        Timber.d("OverlayController: 任务运行中，显示边框")
                    }
                }
            }

            // 离开运行态：恢复主面板
            wasActive && !isActive -> {
                when (currentMode) {
                    OverlayControlMode.ACCESSIBILITY -> {
                        borderOverlayManager.hide()
                        showMainPanel()
                        Timber.d("OverlayController: 任务结束，隐藏边框，恢复主面板")
                    }

                    OverlayControlMode.FLOAT_BALL -> {
                        hideFloatBall()
                        showMainPanel()
                        Timber.d("OverlayController: 任务结束，隐藏悬浮球，恢复主面板")
                    }
                }
                _signal.tryEmit(current)
            }
        }
    }

    fun getNewComposeView(): ComposeView {
        return ComposeView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            fwViewModelOwner.let {
                setViewTreeLifecycleOwner(it)
                setViewTreeViewModelStoreOwner(it)
                setViewTreeSavedStateRegistryOwner(it)
            }
            calculatedPanelLayout?.let {
                layoutParams = ViewGroup.LayoutParams(
                    it.first,
                    it.second
                )
            }
            setContent {
                val themeMode by appSettings.themeMode.collectAsStateWithLifecycle()
                val fontSizeScale by appSettings.fontSizeScale.collectAsStateWithLifecycle()

                MaaMeowTheme(themeMode = themeMode) {
                    val baseDensity = LocalDensity.current
                    val configuration = LocalConfiguration.current
                    // 浮窗跟随主界面页面缩放；fontScale 钳制保持历史行为
                    val effectiveScale = AppSettingsManager.resolveFontSizeScale(
                        stored = fontSizeScale,
                        smallestWidthDp = configuration.smallestScreenWidthDp,
                        fontScale = baseDensity.fontScale,
                    )
                    CompositionLocalProvider(
                        LocalFloatingWindowContext provides true,
                        LocalPagerPreloadState provides remember { PagerPreloadState() },
                        LocalDensity provides Density(
                            density = baseDensity.density,
                            fontScale = UiScale.clampOverlayFontScale(baseDensity.fontScale),
                        )
                    ) {
                        ProvideInputFocusManager {
                            val isLocked by _isLocked.collectAsStateWithLifecycle()
                            val chess by xiangqiViewModel.collectAsStateWithLifecycle()
                            XiangqiForegroundPanel(
                                viewModel = chess,
                                onClose = ::onCloseControlPanel,
                                onHome = { Misc.bringAppToFront(context) },
                                isLocked = isLocked,
                                onLockToggle = { locked ->
                                    _isLocked.value = locked
                                    if (locked) {
                                        lockMainPanelPosition()
                                    } else {
                                        unlockMainPanelPosition()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun calculatePanelLayout(config: Configuration): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val height = minOf(config.screenHeightDp * 0.55f, 500f) * density
        val width = minOf(config.screenWidthDp * 0.72f, 320f) * density
        return width.toInt() to height.toInt()
    }

    private fun applyPanelLayout(config: Configuration, layout: Pair<Int, Int>) {
        val density = context.resources.displayMetrics.density
        val (width, height) = layout
        Timber.d("applyPanelLayout: $width x $height")

        FloatingX.controlOrNull(MAIN_PANEL_TAG)?.let {
            val show = it.isShow()
            if (show) {
                it.hide()
            }
            val screenWidth = config.screenWidthDp * density
            val screenHeight = config.screenHeightDp * density
            val centerX = (screenWidth - width) / 2
            val centerY = (screenHeight - height) / 2
            Timber.d("recalculatePanelLayout: screenSize=${screenWidth}x${screenHeight}, panelSize=${width}x${height}, center=($centerX, $centerY)")
            it.move(centerX, centerY)
            it.updateView(getNewComposeView())
            if (show) {
                it.show()
            }
        }

        FloatingX.controlOrNull(FLOAT_BALL_TAG)?.let {
            if (it.isShow()) {
                val screenWidth = config.screenWidthDp * density
                val screenHeight = config.screenHeightDp * density
                Timber.d("FLOAT_BALL_TAG $screenWidth x $screenHeight")
                val ballX = screenWidth
                val ballY = (screenHeight) / 2
                Timber.d("FLOAT_BALL_TAG $ballX x $ballY")
                it.move(ballX, ballY)
            }
        }
    }

    fun install() {
        if (!FloatingX.isInstalled(MAIN_PANEL_TAG)) {
            FloatingX.install {
                enableComposeSupport()
                setContext(context)
                setTag(MAIN_PANEL_TAG)
                setScopeType(FxScopeType.SYSTEM)
                setLayoutView(getNewComposeView())
                setEnableEdgeAdsorption(false)
                setEnableLog(ENABLE_LOG)
                setGravity(FxGravity.CENTER)
                setEnableSafeArea(false)
                setEnableAnimation(true)
                setDisplayMode(FxDisplayMode.ClickOnly)
                setEnableKeyBoardAdapt(true)
                setKeyBackListener(object : IKeyBackListener {
                    override fun onBackPressed(): Boolean {
                        // 消费返回，避免落到底层 Activity；关闭走面板「隐藏」按钮
                        return true
                    }
                })
            }
        }
        if (!FloatingX.isInstalled(FLOAT_BALL_TAG)) {
            FloatingX.install {
                enableComposeSupport()
                setContext(context)
                setTag(FLOAT_BALL_TAG)
                setScopeType(FxScopeType.SYSTEM)
                setLayoutView(
                    ComposeView(context).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        fwViewModelOwner.let {
                            setViewTreeLifecycleOwner(it)
                            setViewTreeViewModelStoreOwner(it)
                            setViewTreeSavedStateRegistryOwner(it)
                        }
                        setContent {
                            // 悬浮窗需要在后台强行保持监听，不使用 collectAsStateWithLifecycle
                            val runningState by compositionService.state.collectAsState()
                            val countdown by countdownState.collectAsState()
                            FloatBall(
                                onClick = {
                                    if (countdown is CountdownState.Counting) {
                                        handleCountdownClick()
                                    } else {
                                        onFloatBallClick()
                                    }
                                },
                                runningState = runningState,
                                countdownSeconds = (countdown as? CountdownState.Counting)?.remainingSeconds,
                            )
                        }
                    }
                )
                setEnableLog(ENABLE_LOG)
                setEnableEdgeAdsorption(true)
                setGravity(FxGravity.RIGHT_OR_CENTER)
                setEnableAnimation(true)
            }
        }
        Timber.d("OverlayController: install completed")
    }

    fun uninstall() {
        boardCalibration.hide()
        FloatingX.uninstallAll()
        Timber.d("OverlayController: uninstall completed")
    }

    fun onFloatBallClick() {
        if (xiangqiViewModel.value?.xiangqiRecognition?.value?.isAutoPlaying == true) {
            xiangqiViewModel.value?.onStopAutoXiangqi("float-ball")
            hideFloatBall()
            showMainPanel()
            return
        }
        if (_countdownState.value is CountdownState.Counting) {
            handleCountdownClick()
            return
        }
        hideFloatBall()
        if (compositionService.state.value == MaaExecutionState.RUNNING) {
            scope.launch {
                compositionService.stop()
            }
        }
        showMainPanel()
    }

    fun onCloseControlPanel() {
        hideMainPanel()
        if (currentMode == OverlayControlMode.FLOAT_BALL) {
            showFloatBall()
        }
    }

    fun showMainPanel() {
        scope.launch {
            mainPanelVisible.value = true
            fwViewModelOwner.start()
            FloatingX.controlOrNull(MAIN_PANEL_TAG)?.show()
        }
    }

    fun hideMainPanel() {
        scope.launch { hideMainPanelNow() }
    }

    /** 主线程调用 */
    private fun hideMainPanelNow() {
        mainPanelVisible.value = false
        val control = FloatingX.controlOrNull(MAIN_PANEL_TAG)
        if (control?.isShow() == true) {
            control.hide()
            panelHiddenAtMs = SystemClock.uptimeMillis()
        }
        fwViewModelOwner.stop()
    }

    /** 前台开跑前收干净主面板，见 [com.aliothmoon.maameow.domain.service.ForegroundScreenGate] */
    suspend fun awaitPanelCleared() {
        if (!_isActive.value) return
        val hiddenAt = withContext(Dispatchers.Main) {
            hideMainPanelNow()
            panelHiddenAtMs
        }
        val remaining = PANEL_CLEAR_SETTLE_MS - (SystemClock.uptimeMillis() - hiddenAt)
        if (remaining > 0) delay(remaining)
    }

    fun showFloatBall() {
        FloatingX.controlOrNull(FLOAT_BALL_TAG)?.show()
    }

    fun hideFloatBall() {
        FloatingX.controlOrNull(FLOAT_BALL_TAG)?.hide()
    }

    fun lockMainPanelPosition() {
        FloatingX.controlOrNull(MAIN_PANEL_TAG)?.updateConfig {
            setDisplayMode(FxDisplayMode.ClickOnly)
        }
    }

    fun unlockMainPanelPosition() {
        FloatingX.controlOrNull(MAIN_PANEL_TAG)?.updateConfig {
            setDisplayMode(FxDisplayMode.Normal)
        }
    }

    /**
     * 首页手动启动控制层后才 _isActive
     * 定时/LAUNCH_PROFILE 前台静默启动不会走这里，需用户事先 show 过才能在 RUNNING 时出球/边框
     */
    fun show(mode: OverlayControlMode = OverlayControlMode.ACCESSIBILITY) {
        if (appSettings.runMode.value != RunMode.FOREGROUND) {
            Timber.w("OverlayController: 非前台模式，忽略 show 请求")
            return
        }
        install()
        currentMode = mode

        when (mode) {
            OverlayControlMode.ACCESSIBILITY -> {
                // 快捷键模式：隐藏悬浮球，注册音量键监听，显示主面板
                hideFloatBall()
                registerVolumeKeyListener()
                showMainPanel()
                Timber.d("OverlayController: 快捷键模式已启动")
            }

            OverlayControlMode.FLOAT_BALL -> {
                // 悬浮球模式：注销音量键监听，显示悬浮球
                unregisterVolumeKeyListener()
                showFloatBall()
                Timber.d("OverlayController: 悬浮球模式已启动")
            }
        }
        _isActive.value = true
    }

    suspend fun applyMode(mode: OverlayControlMode) {
        if (currentMode == mode) return

        Timber.d("FloatWindowCompose: 切换模式 $currentMode -> $mode")

        // 隐藏当前模式的 UI
        when (currentMode) {
            OverlayControlMode.ACCESSIBILITY -> {
                // 旧模式是快捷键模式，隐藏边框（如果正在显示）
                borderOverlayManager.hide()
            }

            OverlayControlMode.FLOAT_BALL -> {
                // 旧模式是悬浮球模式，隐藏悬浮球
                hideFloatBall()
            }
        }

        // 应用新模式
        currentMode = mode
        when (mode) {
            OverlayControlMode.ACCESSIBILITY -> {
                registerVolumeKeyListener()
                if (compositionService.state.value == MaaExecutionState.RUNNING) {
                    borderOverlayManager.show()
                }
            }

            OverlayControlMode.FLOAT_BALL -> {
                unregisterVolumeKeyListener()
                showFloatBall()
            }
        }
    }

    suspend fun hideAll() {
        boardCalibration.hide()
        xiangqiViewModel.value?.onStopAutoXiangqi("overlay-hidden")
        hideMainPanel()
        hideFloatBall()
        borderOverlayManager.hide()
        unregisterVolumeKeyListener()
        _isActive.value = false
    }

    fun toggleMainPanel(stopSource: String = "panel-toggle") {
        if (xiangqiViewModel.value?.xiangqiRecognition?.value?.isAutoPlaying == true) {
            xiangqiViewModel.value?.onStopAutoXiangqi(stopSource)
        }
        val mainPanelShowing = FloatingX.controlOrNull(MAIN_PANEL_TAG)?.isShow() == true
        if (mainPanelShowing) {
            hideMainPanel()
        } else {
            showMainPanel()
        }
    }

    fun registerVolumeKeyListener() {
        AccessibilityHelperService.setVolumeComboListener {
            scope.launch {
                toggleMainPanel("volume-keys")
                if (compositionService.state.value == MaaExecutionState.RUNNING) {
                    compositionService.stop()
                }
            }
        }
    }

    fun unregisterVolumeKeyListener() {
        AccessibilityHelperService.setVolumeComboListener(null)
    }

}
