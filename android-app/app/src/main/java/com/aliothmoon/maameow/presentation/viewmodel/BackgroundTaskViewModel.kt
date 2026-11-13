package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.domain.launch.LaunchPresentation
import com.aliothmoon.maameow.domain.launch.PlanSideTaskRunner
import com.aliothmoon.maameow.domain.launch.LaunchRequest
import com.aliothmoon.maameow.domain.launch.LaunchSession
import com.aliothmoon.maameow.domain.launch.LaunchUserEvent
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.launch.toCountdownState
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.domain.service.GameFpsWatcher
import com.aliothmoon.maameow.domain.service.GameMuteCoordinator
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.service.ScreenPowerController
import com.aliothmoon.maameow.domain.service.TaskEndRegistry
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.state.isIdle
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardCalibration
import com.aliothmoon.maameow.domain.xiangqi.XiangqiMove
import com.aliothmoon.maameow.domain.xiangqi.XiangqiGameSession
import com.aliothmoon.maameow.domain.xiangqi.XiangqiRules
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.domain.xiangqi.XiangqiNanoV3Recognizer
import com.aliothmoon.maameow.domain.xiangqi.XiangqiRecognitionResult
import com.aliothmoon.maameow.domain.xiangqi.screenPoint
import com.aliothmoon.maameow.domain.xiangqi.PikafishAnalyzer
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.state.BackgroundTaskState
import com.aliothmoon.maameow.presentation.state.PreviewTouchMarker
import com.aliothmoon.maameow.presentation.state.XiangqiRecognitionUiState
import com.aliothmoon.maameow.presentation.state.UiEffect
import com.aliothmoon.maameow.remote.AppAliveStatus
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.service.AccessibilityHelperService
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.aliothmoon.maameow.domain.service.XiangqiAutomationService
import com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardImage
import com.aliothmoon.maameow.domain.xiangqi.XiangqiSquare
import com.aliothmoon.maameow.domain.xiangqi.XiangqiFrameNotReady
import com.aliothmoon.maameow.domain.xiangqi.canRescanXiangqiBeforeInput
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTurnCue
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTurnCueDetector
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class BackgroundTaskViewModel(
    val chainState: TaskChainState,
    private val prepareTaskStart: PrepareTaskStartUseCase,
    private val compositionService: MaaCompositionService,
    private val sessionLogger: MaaSessionLogger,
    private val appSettingsManager: AppSettingsManager,
    private val pathConfig: MaaPathConfig,
    private val achievementReporter: AchievementReporter,
    private val gameMuteCoordinator: GameMuteCoordinator,
    gameFpsWatcher: GameFpsWatcher,
    private val launchPipeline: LaunchPipeline,
    private val taskEndRegistry: TaskEndRegistry,
    private val application: Context,
    private val sideTaskRunner: PlanSideTaskRunner,
    private val screenPower: ScreenPowerController,
    private val appAliveChecker: AppAliveChecker,
    private val xiangqiRecognizer: XiangqiNanoV3Recognizer,
    private val overlayController: OverlayController,
    private val pikafishAnalyzer: PikafishAnalyzer,
) : ViewModel() {

    private companion object {
        const val GAME_ON_DISPLAY_POLL_MS = 2_000L
    }

    val launchSession: StateFlow<LaunchSession> = launchPipeline.session
    val countdownState: StateFlow<CountdownState> = launchPipeline.session
        .map { it.toCountdownState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CountdownState.Idle)

    /**
     * 导航用：仅后台 Dialog 倒计时时置位
     * 前台无倒计时不导航，避免强行拉回主 Tab
     */
    val pendingNavigateRequestId: StateFlow<String?> = launchPipeline.session
        .map { session ->
            when (session) {
                is LaunchSession.InFlight -> {
                    if (session.presentation != LaunchPresentation.DIALOG) null
                    else when (session.phase) {
                        is LaunchSession.Phase.Counting,
                        LaunchSession.Phase.Preparing,
                        LaunchSession.Phase.Starting -> session.request.requestId

                        else -> null
                    }
                }

                LaunchSession.Idle -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(BackgroundTaskState())
    val state: StateFlow<BackgroundTaskState> = _state.asStateFlow()
    val logs: StateFlow<List<LogItem>> = sessionLogger.logs

    private val previewSurface = PreviewSurfaceHandover()
    val previewSurfaceEpoch: StateFlow<Int> = previewSurface.surfaceEpoch

    val isGameMuted: StateFlow<Boolean> = gameMuteCoordinator.isMuted

    // 后台模式游戏实时帧率，null = 未监控
    val gameFps: StateFlow<Float?> = gameFpsWatcher.fps

    @OptIn(ExperimentalCoroutinesApi::class)
    val gameOnDisplay: StateFlow<Boolean> = combine(
        compositionService.backgroundDisplayPackage,
        compositionService.state,
    ) { pkg, maa -> pkg.takeIf { maa == MaaExecutionState.IDLE || maa == MaaExecutionState.ERROR } }
        .distinctUntilChanged()
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(false)
            } else {
                flow {
                    while (true) {
                        emit(appAliveChecker.isAppOnBackgroundDisplay(pkg) == true)
                        delay(GAME_ON_DISPLAY_POLL_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val selectedGameClientType = MutableStateFlow<String?>(null)
    private val _gameStatus = MutableStateFlow(AppAliveStatus.UNKNOWN)
    val gameStatus: StateFlow<Int> = _gameStatus.asStateFlow()
    private val _gameSwitchBusy = MutableStateFlow(false)
    val gameSwitchBusy: StateFlow<Boolean> = _gameSwitchBusy.asStateFlow()

    // 调试截图结果（已本地化的提示文案），供 UI 以 Toast 展示
    private val _screenshotMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val screenshotMessage: SharedFlow<String> = _screenshotMessage.asSharedFlow()

    private val _xiangqiRecognition = MutableStateFlow(XiangqiRecognitionUiState())
    val xiangqiRecognition: StateFlow<XiangqiRecognitionUiState> = _xiangqiRecognition.asStateFlow()
    val xiangqiEngineSupported: Boolean get() = pikafishAnalyzer.isSupported
    val xiangqiModelStatus = xiangqiRecognizer.modelStatus

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private val touchPreviewController = TouchPreviewController(viewModelScope)
    val markers: StateFlow<List<PreviewTouchMarker>> = touchPreviewController.markers
    private var pendingStart: PendingStart? = null

    private data class PendingStart(
        val context: TaskStartContext,
    )

    init {
        Timber.i("BackgroundTaskViewModel inited")
        observeServiceState()
        observeTaskEnd()
        observeTouchPreviewToggle()
        observeDefaultTaskSelection()
        observeGameStatus()
        viewModelScope.launch { xiangqiRecognizer.refreshModelStatus() }
    }

    private fun observeGameStatus() {
        viewModelScope.launch {
            merge(
                compositionService.state.map { },
                compositionService.backgroundDisplayPackage.map { },
                RemoteServiceManager.state.map { },
                taskEndRegistry.taskEnded.map { },
            ).collectLatest { updateGameStatus() }
        }
    }

    fun refreshGameStatus(clientType: String? = null) {
        if (clientType != null) selectedGameClientType.value = clientType
        viewModelScope.launch { updateGameStatus() }
    }

    // 启停进行中不刷新，免得拉起前的旧状态盖掉结果
    private suspend fun updateGameStatus() {
        val status = currentGamePackage()?.let { appAliveChecker.isAppAlive(it) } ?: AppAliveStatus.UNKNOWN
        if (!_gameSwitchBusy.value) _gameStatus.value = status
    }

    private fun currentGamePackage(): String? {
        val clientType = selectedGameClientType.value
            ?: if (compositionService.state.value.isIdle) {
                chainState.clientType
            } else {
                chainState.lastUsedClientType ?: chainState.clientType
            }
        return compositionService.backgroundDisplayPackage.value ?: Packages[clientType]
    }

    /**
     * 前台模式通过 Android 启动器拉起游戏，不要求 Shizuku / Root。
     * 后台模式复用远端虚拟显示启动链；已运行的游戏会被移入目标显示器。
     */
    fun startGame(clientType: String? = null) {
        val targetClientType = clientType ?: chainState.clientType
        selectedGameClientType.value = targetClientType
        val pkg = Packages[targetClientType] ?: return
        if (!compositionService.state.value.isIdle || !_gameSwitchBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            var running = false
            try {
                running = if (appSettingsManager.runMode.value == RunMode.FOREGROUND) {
                    val intent = application.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent == null) {
                        false
                    } else {
                        runCatching {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            application.startActivity(intent)
                            true
                        }.onFailure { Timber.e(it, "Failed to launch game: %s", pkg) }
                            .getOrDefault(false)
                    }
                } else {
                    compositionService.launchGame(targetClientType)
                }
                if (running) _gameStatus.value = AppAliveStatus.ALIVE
                else _effects.send(UiEffect.toast(R.string.bg_action_start_game_failed))
            } finally {
                _gameSwitchBusy.value = false
            }
            if (!running) updateGameStatus()
        }
    }

    fun closeGame(clientType: String? = null) {
        if (clientType != null) selectedGameClientType.value = clientType
        val pkg = currentGamePackage() ?: return
        if (!_gameSwitchBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                if (!compositionService.closeGame(pkg)) {
                    _effects.send(UiEffect.toast(R.string.bg_action_close_game_failed))
                }
            } finally {
                _gameSwitchBusy.value = false
            }
            updateGameStatus()
        }
    }

    /**
     * 首次进入 / 选中失效时默认打开任务链第一项，避免右侧一直停在空占位。
     * 新增任务、配置管理模式下不自动改写选中。
     */
    private fun observeDefaultTaskSelection() {
        viewModelScope.launch {
            combine(chainState.chain, _state) { nodes, ui ->
                resolveTaskPanelSelectedNodeId(
                    nodes = nodes,
                    selectedNodeId = ui.selectedNodeId,
                    isAddingTask = ui.isAddingTask,
                    isProfileMode = ui.isProfileMode,
                )
            }
                .distinctUntilChanged()
                .collect { resolved ->
                    if (_state.value.selectedNodeId != resolved) {
                        _state.update { it.copy(selectedNodeId = resolved) }
                    }
                }
        }
    }

    private fun observeTouchPreviewToggle() {
        viewModelScope.launch {
            appSettingsManager.showTouchPreview.collect { enabled ->
                touchPreviewController.onTouchCallbackChange(enabled)
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            RemoteServiceManager.state
                .drop(1)
                .collect { state ->
                    when (state) {
                        // 服务重连
                        is RemoteServiceManager.ServiceState.Connected -> {
                            onServiceReconnected(state.service)
                        }

                        is RemoteServiceManager.ServiceState.Error -> {
                            touchPreviewController.onClear()
                        }

                        else -> Unit
                    }
                }
        }
    }

    fun onServiceReconnected(srv: RemoteService) {
        previewSurface.onServiceConnected(srv)
        val enabled = appSettingsManager.showTouchPreview.value
        touchPreviewController.onTouchCallbackChange(enabled)
    }

    private fun observeTaskEnd() {
        viewModelScope.launch {
            taskEndRegistry.taskEnded.collect { reason ->
                // 关游戏由 TaskEndRegistry 做，这里只提示
                if (reason != TaskEndRegistry.Reason.MANUAL
                    && appSettingsManager.closeAppOnTaskEnd.value
                ) {
                    _effects.send(UiEffect.toast(R.string.bg_toast_auto_closed_on_end))
                }
            }
        }
    }


    // ==================== Scheduled Launch ====================

    fun onExternalLaunch(request: LaunchRequest) {
        launchPipeline.execute(request)
    }

    fun onScheduledCountdownCancel() {
        launchPipeline.submit(LaunchUserEvent.Cancel)
    }

    fun onScheduledStartNow() {
        launchPipeline.submit(LaunchUserEvent.StartNow)
    }

    fun onNavigateForScheduledLaunch() {
        _state.update {
            it.copy(
                current = PanelTab.TASKS,
                selectedNodeId = null,
                isAddingTask = false,
                isEditMode = false,
                isProfileMode = false,
            )
        }
    }

    // ==================== Surface ====================

    fun onSurfaceAvailable(surface: Surface) {
        previewSurface.attach(surface, RemoteServiceManager.getInstanceOrNull())
    }

    fun onSurfaceDestroyed() {
        previewSurface.detach(RemoteServiceManager.getInstanceOrNull())?.release()
    }

    // ==================== Touch Input ====================

    fun onTouchDown(x: Int, y: Int, contact: Int) {
        if (_xiangqiRecognition.value.isAutoPlaying) onStopAutoXiangqi()
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchDown(x, y, contact)
        }.onFailure {
            Timber.e(it, "touchDown failed at ($x, $y) contact=$contact")
        }
    }

    fun onTouchMove(x: Int, y: Int, contact: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchMove(x, y, contact)
        }.onFailure {
            Timber.e(it, "touchMove failed at ($x, $y) contact=$contact")
        }
    }

    fun onTouchUp(x: Int, y: Int, contact: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchUp(x, y, contact)
        }.onFailure {
            Timber.e(it, "touchUp failed at ($x, $y) contact=$contact")
        }
    }

    fun onTouchCancel() {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchCancel()
        }.onFailure {
            Timber.e(it, "touchCancel failed")
        }
    }

    fun onScreenOff() {
        // 开关开启时 MainActivity 常亮，系统不会休眠；同步 binder，首次还要加载 DisplayControl，不放主线程
        viewModelScope.launch(Dispatchers.IO) {
            screenPower.powerOff(ScreenPowerController.Origin.MANUAL)
        }
    }

    // ==================== Task Chain ====================

    fun onNodeEnabledChange(nodeId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { chainState.setNodeEnabled(nodeId, enabled) }
                .onFailure { e ->
                    Timber.e(e, "Failed to update node enabled: ${e.message}")
                }
        }
    }

    fun onNodeMove(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderNodes(fromIndex, toIndex) }
                .onFailure { e ->
                    Timber.e(e, "Failed to reorder nodes: ${e.message}")
                }
        }
    }

    fun onNodeSelected(nodeId: String) {
        _state.update { it.copy(selectedNodeId = nodeId, isAddingTask = false) }
    }

    fun onToggleEditMode() {
        _state.update {
            it.copy(
                isEditMode = !it.isEditMode,
                isAddingTask = false,
                isProfileMode = false
            )
        }
        Timber.d("Edit mode toggled: %s", _state.value.isEditMode)
    }

    fun onToggleProfileMode() {
        _state.update {
            it.copy(
                isProfileMode = !it.isProfileMode,
                isEditMode = false,
                isAddingTask = false
            )
        }
        Timber.d("Profile mode toggled: %s", _state.value.isProfileMode)
    }

    fun onSwitchProfile(profileId: String) {
        viewModelScope.launch {
            chainState.switchProfile(profileId)
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onCreateProfile() {
        viewModelScope.launch {
            chainState.createProfile()
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onDeleteProfile(profileId: String) {
        viewModelScope.launch {
            chainState.removeProfile(profileId)
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onRenameProfile(profileId: String, name: String) {
        viewModelScope.launch {
            chainState.renameProfile(profileId, name)
        }
    }

    fun onDuplicateProfile(profileId: String) {
        viewModelScope.launch {
            chainState.duplicateProfile(profileId)
        }
    }

    fun onReorderProfile(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderProfiles(fromIndex, toIndex) }
                .onFailure { e -> Timber.e(e, "Failed to reorder profile: ${e.message}") }
        }
    }

    fun onToggleAddingTask() {
        _state.update { it.copy(isAddingTask = !it.isAddingTask, selectedNodeId = null) }
        Timber.d("Adding task mode toggled: %s", _state.value.isAddingTask)
    }

    fun onAddNode(typeInfo: TaskTypeInfo) {
        viewModelScope.launch {
            val nodeId = chainState.addNode(typeInfo)
            _state.update { it.copy(isAddingTask = false, selectedNodeId = nodeId) }
        }
    }

    fun onRemoveNode(nodeId: String) {
        viewModelScope.launch {
            chainState.removeNode(nodeId)
            if (_state.value.selectedNodeId == nodeId) {
                _state.update { it.copy(selectedNodeId = null) }
            }
        }
    }

    fun onDuplicateNode(nodeId: String) {
        viewModelScope.launch {
            val newId = chainState.duplicateNode(nodeId)
            if (newId.isNotEmpty()) {
                _state.update { it.copy(selectedNodeId = newId) }
            }
        }
    }

    fun onRenameNode(nodeId: String, newName: String) {
        viewModelScope.launch {
            chainState.renameNode(nodeId, newName)
        }
    }

    fun onNodeConfigChange(nodeId: String, config: TaskParamProvider) {
        viewModelScope.launch {
            chainState.updateNodeConfig(nodeId, config)
        }
    }

    // ==================== UI State ====================

    fun onToggleFullscreenMonitor() {
        _state.update { it.copy(isFullscreenMonitor = !it.isFullscreenMonitor) }
    }

    /** 进入画中画时调用：全屏预览锁了横屏并收了系统栏，留着会干扰小窗 */
    fun onExitFullscreenMonitor() {
        if (!_state.value.isFullscreenMonitor) return
        _state.update { it.copy(isFullscreenMonitor = false) }
    }

    fun onTabChange(tab: PanelTab) {
        _state.update { it.copy(current = tab) }
    }

    // ==================== Task Execution ====================

    fun onStartTasks() {
        launchManualStart(TaskStartContext(mode = TaskStartMode.MANUAL))
    }

    fun onStartTasksFrom(nodeId: String) {
        launchManualStart(TaskStartContext(mode = TaskStartMode.MANUAL, fromNodeId = nodeId))
    }

    private fun launchManualStart(context: TaskStartContext) {
        viewModelScope.launch {
            val message = startTasksInternal(context = context)
            if (message != null && state.value.dialog == null) {
                showStartFailedDialog(message)
            }
        }
    }

    /** 手动启动（定时走 [LaunchPipeline] + [StartTaskChainUseCase]）。 */
    private suspend fun startTasksInternal(
        context: TaskStartContext,
    ): UiText? {
        val plan = when (
            val decision = prepareTaskStart(
                chain = chainState.chain.value,
                context = context,
            )
        ) {
            is TaskStartDecision.Ready -> {
                pendingStart = null
                decision.plan
            }

            is TaskStartDecision.Blocked -> {
                pendingStart = null
                val message = decision.message
                Timber.w("Validation failed: %s", message.resolve(application))
                showDialog(application.createStartBlockedDialog(message))
                return message
            }

            is TaskStartDecision.RequiresConfirmation -> {
                pendingStart = PendingStart(context.acknowledged(decision.acknowledgement))
                val message = decision.message
                showDialog(application.createStartWarningDialog(decision.acknowledgement, message))
                return message
            }
        }

        if (plan.params.isEmpty()) {
            // 只有旁路任务，不起 Core；界面上没运行态，靠 Toast 报开始与结果
            // 旁路任务跑完才返回，先切页才能边跑边看日志
            onTabChange(PanelTab.LOG)
            sideTaskRunner.runWithoutCore(plan.sideTasks) { _effects.send(UiEffect.toast(it)) }
            return null
        }

        // 必须先于静音，换进程会让旧进程收尾时解除静音
        compositionService.prepareResources(plan.clientType)

        // 先静音后拉起游戏：appops 状态持久，提前设置零成本，消除游戏启动初期的漏音空窗
        val muteRequested = appSettingsManager.muteOnGameLaunch.value
        if (muteRequested && !gameMuteCoordinator.mute(plan.clientType)) {
            _effects.send(UiEffect.toast(R.string.bg_toast_mute_failed))
        }

        val result = compositionService.start(
            tasks = plan.params,
            clientType = plan.clientType,
            preflightLogs = plan.logs,
            fallbacks = plan.fallbacks,
        )
        if (result is MaaCompositionService.StartResult.Success) {
            // 晚于 startSession 发出，拉取日志才有会话接住
            sideTaskRunner.launch(plan.sideTasks)
            achievementReporter.reportTaskStarted(
                taskCount = plan.params.size,
                launchesGame = plan.launchesGame,
                gameAliveBeforeStart = plan.gameAliveBeforeStart,
            )
        }

        val message = application.resolveTaskStartFailureMessage(result)
        if (message != null) {
            Timber.w("Start failed: %s", message.resolve(application))
            return message
        }
        onTabChange(PanelTab.LOG)
        return null
    }

    fun onStopTasks() {
        achievementReporter.reportTaskStopped()
        viewModelScope.launch {
            compositionService.stop()
        }
    }

    fun onClearLogs() {
        sessionLogger.clearRuntimeLogs()
    }

    fun onToggleGameSound() {
        viewModelScope.launch {
            val ok = gameMuteCoordinator.toggle(chainState.clientType)
            if (!ok) {
                _effects.send(UiEffect.toast(R.string.bg_toast_mute_failed))
            }
        }
    }

    /**
     * 请求远端进程抓取当前帧缓冲并保存 PNG 到 {coreRootDir}/debug/screenshots，
     * 结果通过 [screenshotMessage] 反馈给 UI。
     *
     * 由远端（shell 进程）直接落盘——它对 userDir/debug 有写权限（同 logcat 抓取），
     * 避免跨进程读取 ashmem 被 SELinux 拒绝。
     */
    fun onCaptureScreenshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedName = runCatching {
                RemoteServiceManager.getInstanceOrNull()
                    ?.captureFramePng(pathConfig.coreDebugScreenshotsDir)
                    ?.let { File(it).name }
            }.onFailure { Timber.e(it, "captureScreenshot failed") }
                .getOrNull()
            val message = savedName
                ?.let { application.getString(R.string.bg_toast_screenshot_saved, it) }
                ?: application.getString(R.string.bg_toast_screenshot_failed)
            _screenshotMessage.tryEmit(message)
        }
    }

    private fun showStartFailedDialog(message: UiText) {
        showDialog(application.createStartFailedDialog(message))
    }

    fun onDownloadXiangqiModel() {
        viewModelScope.launch {
            _xiangqiRecognition.update { it.copy(error = null) }
            try {
                xiangqiRecognizer.downloadModel()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "download Xiangqi recognition model failed")
                _xiangqiRecognition.update {
                    it.copy(error = application.getString(R.string.xiangqi_recognition_error_generic))
                }
            }
        }
    }


    private var xiangqiJob: Job? = null
    private var xiangqiHintWatchJob: Job? = null

    private fun launchXiangqiOperation(name: String, automatic: Boolean = false, action: suspend (XiangqiTrace) -> Unit) {
        val trace = XiangqiTrace(name)
        if (xiangqiJob?.isCompleted == false) {
            trace.event("blocked", "reason=busy")
            return
        }
        xiangqiHintWatchJob?.cancel()
        _xiangqiRecognition.update { it.copy(isAutoPlaying = automatic, isStoppingAutomation = false, error = null, moveError = null,
            analysisError = null, moveVerified = false, automationStatus = null) }
        xiangqiJob = viewModelScope.launch {
            var serviceRun: XiangqiAutomationService.Companion.Run? = null
            try {
                if (automatic) serviceRun = XiangqiAutomationService.start(application, ::onStopAutoXiangqi)
                action(trace)
            } catch (error: CancellationException) {
                trace.event("cancelled", "inputUnverified=${_xiangqiRecognition.value.inputUnverified}")
                throw error
            } catch (error: LinkageError) {
                trace.failure("runtime", error)
                _xiangqiRecognition.update { it.copy(error = application.getString(R.string.xiangqi_recognition_error_generic)) }
            } catch (error: Exception) {
                trace.failure("stopped", error)
                _xiangqiRecognition.update { it.copy(moveError = application.getString(
                    if (it.inputUnverified) R.string.xiangqi_move_unverified else R.string.xiangqi_automation_paused)) }
            } finally {
                serviceRun?.let(XiangqiAutomationService::finish)
                _xiangqiRecognition.update { it.copy(isRecognizing = false, isAnalyzing = false,
                    isExecutingMove = false, isAutoPlaying = false, isStoppingAutomation = false) }
                startXiangqiHintWatcher()
            }
        }
    }

    fun onRecognizeXiangqiBoard() = launchXiangqiOperation("recognize") { trace ->
        withXiangqiGame { mode, pkg ->
            _xiangqiRecognition.update { it.copy(isRecognizing = true) }
            val result = captureAndRecognizeXiangqi(mode, pkg, trace)
            acceptXiangqiObservation(result, trace)
            if (result.isComplete) _xiangqiRecognition.update { it.copy(inputUnverified = false) }
        }
    }

    private suspend fun <T> withXiangqiGame(action: suspend (RunMode, String?) -> T): T {
        val mode = appSettingsManager.runMode.value
        return if (mode == RunMode.FOREGROUND) withForegroundXiangqiGame { action(mode, it) }
        else action(mode, null)
    }

    private suspend fun <T> withForegroundXiangqiGame(action: suspend (String) -> T): T {
        val pkg = Packages["Xiangqi"] ?: error("Xiangqi package missing")
        val floating = overlayController.isActive.value
        overlayController.setXiangqiCaptureActive(true)
        return try {
            if (floating) {
                overlayController.hideFloatBall()
                overlayController.awaitPanelCleared()
            }
            if (!AccessibilityHelperService.isGameInForeground(pkg)) {
                val intent = application.packageManager.getLaunchIntentForPackage(pkg)
                    ?: error("Xiangqi app is not installed")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AccessibilityHelperService.expectGameWindow()
                application.startActivity(intent)
            }
            action(pkg)
        } finally {
            overlayController.setXiangqiCaptureActive(false)
            runCatching {
                if (floating || overlayController.isActive.value) {
                    overlayController.hideFloatBall()
                    overlayController.showMainPanel()
                } else Misc.bringAppToFront(application)
            }.onFailure { Timber.e(it, "return from Xiangqi game failed") }
        }
    }

    private suspend fun captureBackgroundXiangqiFrame(): ByteArray {
        val jpeg = withContext(Dispatchers.IO) {
            RemoteServiceManager.useRemoteService(timeoutMs = 8_000L) { service ->
                RemoteServiceManager.callOrRestart(
                    service = service, call = "captureFrameJpeg", timeoutMs = 5_000L,
                    onAbandoned = { Timber.e(it, "capture Xiangqi frame was abandoned") },
                ) { remote -> remote.captureFrameJpeg(92) }
            }
        }
        return jpeg?.takeIf { it.isNotEmpty() }
            ?: error(application.getString(R.string.xiangqi_recognition_no_frame))
    }

    private suspend fun captureXiangqiBitmap(mode: RunMode, pkg: String?): Bitmap {
        check(mode == appSettingsManager.runMode.value) { "Run mode changed" }
        return if (mode == RunMode.FOREGROUND) {
            overlayController.withXiangqiStatusCleared(XiangqiBoardCalibration.PHONE_1080X2400) {
                AccessibilityHelperService.captureGameBitmap(pkg ?: error("Xiangqi package missing"))
            }
        } else {
            check(gameOnDisplay.value) { "Game left virtual display" }
            withContext(Dispatchers.Default) {
                val jpeg = captureBackgroundXiangqiFrame()
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    ?: error("Cannot decode game frame")
            }
        }
    }

    private fun xiangqiCalibration(mode: RunMode) = if (mode == RunMode.FOREGROUND) {
        XiangqiBoardCalibration.PHONE_1080X2400
    } else XiangqiBoardCalibration.MUMU_900X1600

    private suspend fun captureStableXiangqiBitmap(mode: RunMode, pkg: String?, trace: XiangqiTrace): Bitmap {
        var previous: XiangqiBoardImage? = null
        repeat(8) { sample ->
            val bitmap = captureXiangqiBitmap(mode, pkg)
            var retained = false
            try {
                val image = withContext(Dispatchers.Default) { sampleXiangqiBitmap(bitmap, mode) }
                val stable = previous?.matches(image) == true
                trace.event("capture.stability", "sample=$sample stable=$stable")
                if (stable) {
                    retained = true
                    return bitmap
                }
                previous = image
            } finally { if (!retained) bitmap.recycle() }
            delay(120)
        }
        throw XiangqiFrameNotReady("Board animation did not settle")
    }

    private fun sampleXiangqiBitmap(bitmap: Bitmap, mode: RunMode): XiangqiBoardImage {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return XiangqiBoardImage.sample(pixels, bitmap.width, bitmap.height,
            xiangqiCalibration(mode).scaledTo(bitmap.width, bitmap.height))
    }

    private suspend fun captureAndRecognizeXiangqi(mode: RunMode, pkg: String?, trace: XiangqiTrace, stable: Boolean = false): XiangqiRecognitionResult {
        trace.event("capture.request", "mode=$mode")
        val bitmap = if (stable) captureStableXiangqiBitmap(mode, pkg, trace) else captureXiangqiBitmap(mode, pkg)
        trace.event("capture.received", "frame=${bitmap.width}x${bitmap.height}")
        _xiangqiRecognition.update { it.copy(automationStatus =
            if (it.isExecutingMove) application.getString(R.string.xiangqi_auto_verifying) else null) }
        try {
            val result = xiangqiRecognizer.recognizeBitmap(bitmap, xiangqiCalibration(mode), trace = trace)
            val uncertain = result.predictions.filter { XiangqiSquare(it.row, it.col) in result.uncertainSquares }
                .joinToString { "${'a' + it.col}${9 - it.row}:${it.symbol}=${it.confidence}" }
            trace.event("recognition", "complete=${result.isComplete} uncertain=${result.uncertainSquares.size} details=$uncertain confidence=${result.meanConfidence} problem=${result.problemCode} board=${result.fenBoard}")
            if (!result.isComplete) withContext(Dispatchers.IO) {
                runCatching {
                    val file = java.io.File(application.filesDir, "diagnostics/xiangqi-last-failed-board.jpg")
                    file.parentFile?.mkdirs()
                    val c = result.calibration
                    val left = c.left.toInt(); val top = c.top.toInt()
                    val crop = Bitmap.createBitmap(bitmap, left, top, (c.right - left).toInt() + 1, (c.bottom - top).toInt() + 1)
                    try { file.outputStream().use { check(crop.compress(Bitmap.CompressFormat.JPEG, 95, it)) } }
                    finally { if (crop !== bitmap) crop.recycle() }
                    trace.event("diagnostic.saved", "file=xiangqi-last-failed-board.jpg")
                }.onFailure { trace.failure("diagnostic.save", it) }
            }
            return result
        } finally { bitmap.recycle() }
    }

    private suspend fun captureXiangqiReference(
        mode: RunMode, pkg: String?, before: XiangqiRecognitionResult,
    ): Pair<XiangqiBoardImage, XiangqiTurnCue?> =
        withContext(Dispatchers.Default) {
            val bitmap = captureXiangqiBitmap(mode, pkg)
            try {
                check(bitmap.width == before.calibration.sourceWidth && bitmap.height == before.calibration.sourceHeight)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val image = XiangqiBoardImage.sample(
                    pixels, bitmap.width, bitmap.height, before.calibration)
                val cue = XiangqiTurnCueDetector.detect(
                    pixels, bitmap.width, bitmap.height, before.calibration, before.rows, before.redAtTopOnScreen)
                image to cue
            } finally { bitmap.recycle() }
        }

    private fun acceptXiangqiObservation(result: XiangqiRecognitionResult, trace: XiangqiTrace) {
        _xiangqiRecognition.update { old ->
            var session = old.session
            var side: Char? = null
            if (result.isComplete) {
                val connected = session?.let { runCatching { it.observe(result.rows) }.getOrNull() }
                side = if (connected != null && result.turnCue != null && connected.side != result.turnCue.sideToMove) {
                    trace.event("position.conflict", "historySide=${connected.side} visualSide=${result.turnCue.sideToMove}")
                    null
                } else result.turnCue?.sideToMove ?: connected?.side ?: XiangqiGameSession.opening(result.rows)?.side
                session = XiangqiGameSession.reconnect(session, result.rows, side)
                if (session?.initialFen != old.session?.initialFen || session?.historyKnown != old.session?.historyKnown) {
                    trace.event("position.rebased", "historyKnown=${session?.historyKnown} side=$side")
                }
            }
            val unchanged = result.isComplete && old.result?.isComplete == true &&
                result.rows == old.result.rows && side == old.analysis?.side
            trace.event("position", "side=$side player=${result.playerSide} visualTurn=${result.turnCue?.sideToMove} historyKnown=${session?.historyKnown}")
            old.copy(isRecognizing = false, result = result, session = session, detectedSide = side,
                historyError = null, error = null, boardChanged = !result.isComplete,
                analysis = old.analysis.takeIf { unchanged }, analysisError = null)
        }
    }

    fun onAnalyzeXiangqiBoard(side: Char) = launchXiangqiOperation("analyze") { trace ->
        analyzeXiangqiBoard(side, trace)
        if (appSettingsManager.runMode.value == RunMode.FOREGROUND && overlayController.isActive.value) {
            overlayController.onCloseControlPanel()
        }
    }

    private suspend fun analyzeXiangqiBoard(side: Char, trace: XiangqiTrace) {
        val state = _xiangqiRecognition.value
        val recognized = state.result?.takeIf { it.isComplete } ?: error("Incomplete board")
        check(!state.boardChanged)
        check(recognized.turnCue == null || recognized.turnCue.sideToMove == side) { "Visual turn disagrees with requested analysis" }
        val session = XiangqiGameSession.reconnect(state.session, recognized.rows, side) ?: error("Unknown turn")
        _xiangqiRecognition.update { it.copy(isAnalyzing = true, analysisError = null, analysis = null,
            session = session, detectedSide = side) }
        trace.event("engine.request", "side=$side historyKnown=${session.historyKnown} moves=${session.moves.size}")
        try {
            val analysis = pikafishAnalyzer.analyze(session, trace)
            trace.event("engine.result", "move=${analysis.bestMove} depth=${analysis.depth} score=${analysis.score}")
            _xiangqiRecognition.update { it.copy(analysis = analysis) }
        } finally { _xiangqiRecognition.update { it.copy(isAnalyzing = false) } }
    }

    fun onExecuteXiangqiMove() = launchXiangqiOperation("execute") { trace ->
        withXiangqiGame { mode, pkg -> executeXiangqiMove(mode, pkg, trace) }
    }

    fun onAutoXiangqiMove(side: Char? = null) = launchXiangqiOperation("recognize-and-play") { trace ->
        check(!_xiangqiRecognition.value.inputUnverified) { "Previous input is unverified" }
        withXiangqiGame { mode, pkg ->
            observeStableXiangqiBoard(mode, pkg, trace)
            val currentSide = _xiangqiRecognition.value.detectedSide ?: side
            if (currentSide == null || currentSide != _xiangqiRecognition.value.result?.playerSide) {
                _xiangqiRecognition.update { it.copy(automationStatus = application.getString(R.string.xiangqi_auto_wait_turn)) }
                return@withXiangqiGame
            }
            analyzeXiangqiBoard(currentSide, trace)
            executeXiangqiMove(mode, pkg, trace)
        }
    }

    fun onStartAutoXiangqi(side: Char? = null) = launchXiangqiOperation("auto-play", automatic = true) { trace ->
        check(!_xiangqiRecognition.value.inputUnverified) { "Previous input is unverified" }
        withXiangqiGame { mode, pkg ->
            if (mode == RunMode.FOREGROUND) overlayController.showXiangqiAutomationControl()
            observeStableXiangqiBoard(mode, pkg, trace)
            var first = true
            while (true) {
                currentCoroutineContext().ensureActive()
                val state = _xiangqiRecognition.value
                val result = state.result?.takeIf { it.isComplete } ?: error("Incomplete board")
                val currentSide = state.detectedSide ?: side.takeIf { first }
                first = false
                val legal = currentSide?.let { XiangqiRules.legalMoves(result.rows, it) }
                trace.event("auto.decision", "side=$currentSide player=${result.playerSide} legalMoves=${legal?.size} historyKnown=${state.session?.historyKnown}")
                if (currentSide == result.playerSide && !legal.isNullOrEmpty()) {
                    _xiangqiRecognition.update { it.copy(automationStatus = application.getString(R.string.xiangqi_auto_thinking)) }
                    analyzeXiangqiBoard(currentSide, trace)
                    try {
                        executeXiangqiMove(mode, pkg, trace)
                    } catch (error: Exception) {
                        if (!canRescanXiangqiBeforeInput(error, _xiangqiRecognition.value.inputUnverified)) throw error
                        trace.event("auto.rescan", "reason=${error.message} inputSent=false")
                        observeStableXiangqiBoard(mode, pkg, trace)
                        continue
                    }
                    if (_xiangqiRecognition.value.detectedSide == result.playerSide) continue
                } else {
                    _xiangqiRecognition.update { it.copy(automationStatus = application.getString(
                        if (legal?.isEmpty() == true) R.string.xiangqi_auto_wait_new_board else R.string.xiangqi_auto_wait_turn)) }
                }
                val observed = _xiangqiRecognition.value.result ?: error("Missing board")
                do {
                    delay(500)
                    val reference = captureXiangqiReference(mode, pkg, observed)
                    val changed = observed.boardImage?.matches(reference.first) != true
                    val turnChanged = reference.second?.sideToMove?.let { it != _xiangqiRecognition.value.detectedSide } == true
                } while (!changed && !turnChanged)
                observeStableXiangqiBoard(mode, pkg, trace)
            }
        }
    }

    fun onStopAutoXiangqi() {
        if (xiangqiJob?.isCompleted != false) return
        XiangqiTrace("auto-stop").event("requested", "inputUnverified=${_xiangqiRecognition.value.inputUnverified}")
        xiangqiJob?.cancel()
        _xiangqiRecognition.update { it.copy(isAutoPlaying = false, isStoppingAutomation = true,
            automationStatus = application.getString(R.string.xiangqi_auto_stopped),
            moveError = if (it.inputUnverified) application.getString(R.string.xiangqi_move_unverified) else it.moveError) }
    }

    private suspend fun observeStableXiangqiBoard(mode: RunMode, pkg: String?, trace: XiangqiTrace) {
        _xiangqiRecognition.update { it.copy(isRecognizing = true, analysis = null, automationStatus = null) }
        while (true) {
            val result = try {
                captureAndRecognizeXiangqi(mode, pkg, trace, stable = true)
            } catch (error: Exception) {
                if (!_xiangqiRecognition.value.isAutoPlaying ||
                    !canRescanXiangqiBeforeInput(error, _xiangqiRecognition.value.inputUnverified)) throw error
                trace.event("auto.paused", "reason=animation noInput=true")
                _xiangqiRecognition.update { it.copy(automationStatus = application.getString(R.string.xiangqi_auto_wait_stable)) }
                delay(250)
                continue
            }
            if (result.isComplete) {
                acceptXiangqiObservation(result, trace)
                return
            }
            check(_xiangqiRecognition.value.isAutoPlaying) { "Incomplete or animated board" }
            trace.event("auto.paused", "reason=unconfirmed-board noInput=true")
            _xiangqiRecognition.update { it.copy(automationStatus = application.getString(R.string.xiangqi_auto_wait_new_board)) }
            delay(1000)
        }
    }

    private suspend fun executeXiangqiMove(mode: RunMode, pkg: String?, trace: XiangqiTrace) {
        val started = SystemClock.uptimeMillis()
        val state = _xiangqiRecognition.value
        val before = state.result?.takeIf { it.isComplete } ?: error("Incomplete board")
        val analysis = state.analysis ?: error("Missing analysis")
        val session = state.session ?: error("Unknown position")
        val move = XiangqiMove.parse(analysis.bestMove, before.rows, analysis.side) ?: error("Invalid move")
        check(!state.inputUnverified && !state.boardChanged && session.side == before.playerSide &&
            session.rows == before.rows && session.positionCommand == analysis.positionCommand &&
            XiangqiRules.isLegal(before.rows, session.side, move)) { "Stale or unsafe recommendation" }
        _xiangqiRecognition.update { it.copy(isExecutingMove = true, moveError = null, moveVerified = false,
            automationStatus = application.getString(R.string.xiangqi_move_running)) }
        try {
            suspend fun verifyAndSend() {
                val (image, cue) = captureXiangqiReference(mode, pkg, before)
                if (before.boardImage?.matches(image) != true ||
                    (cue != null && cue.sideToMove != session.side)) {
                    _xiangqiRecognition.update { it.copy(analysis = null, boardChanged = true) }
                    throw XiangqiFrameNotReady("Board or turn changed before input")
                }
                trace.event("verify.before", "visualMatch=true elapsedMs=${SystemClock.uptimeMillis() - started}")
                check(appSettingsManager.runMode.value == mode)
                val targetCalibration = if (mode == RunMode.BACKGROUND) {
                    check(gameOnDisplay.value)
                    val resolution = compositionService.displayResolution.value
                    val target = if (resolution.width == DefaultDisplayConfig.WIDTH && resolution.height == DefaultDisplayConfig.HEIGHT) {
                        DefaultDisplayConfig.RES_XIANGQI
                    } else resolution
                    before.calibration.scaledTo(target.width, target.height)
                } else before.calibration
                val from = move.from.screenPoint(targetCalibration, before.redAtTopOnScreen)
                val to = move.to.screenPoint(targetCalibration, before.redAtTopOnScreen)
                trace.event("input.coordinates", "from=$from to=$to move=${move.iccs}")
                currentCoroutineContext().ensureActive()
                _xiangqiRecognition.update { it.copy(inputUnverified = true) }
                if (mode == RunMode.FOREGROUND) {
                    AccessibilityHelperService.tapGameMove(pkg ?: error("Xiangqi package missing"), from.x, from.y, to.x, to.y)
                } else {
                    val remote = RemoteServiceManager.getInstanceOrNull() ?: error("Input service unavailable")
                    try {
                        remote.touchDown(from.x, from.y, 0)
                        delay(60)
                        remote.touchUp(from.x, from.y, 0)
                        delay(120)
                        remote.touchDown(to.x, to.y, 0)
                        delay(60)
                        remote.touchUp(to.x, to.y, 0)
                    } finally { remote.touchCancel() }
                }
            }
            if (mode == RunMode.FOREGROUND) {
                overlayController.withXiangqiStatusCleared(before.calibration) { verifyAndSend() }
            } else verifyAndSend()
            val inputMs = SystemClock.uptimeMillis() - started
            trace.event("input.sent", "inputMs=$inputMs operationMs=${trace.elapsedMs} withinTwoSeconds=${inputMs <= 2000}")
            _xiangqiRecognition.update { it.copy(automationStatus = application.getString(R.string.xiangqi_auto_verifying)) }
            val expected = XiangqiRules.apply(before.rows, move)
            val opponent = if (session.side == 'w') 'b' else 'w'
            delay(120)
            for (sample in 0 until 4) {
                val after = try {
                    captureAndRecognizeXiangqi(mode, pkg, trace, stable = true)
                } catch (error: XiangqiFrameNotReady) {
                    trace.event("verify.waiting", "sample=$sample reason=animation inputNotRepeated=true")
                    _xiangqiRecognition.update { it.copy(automationStatus = application.getString(R.string.xiangqi_auto_wait_stable)) }
                    delay(150)
                    continue
                }
                val valid = after.isComplete && after.calibration == before.calibration &&
                    after.redAtTopOnScreen == before.redAtTopOnScreen &&
                    (after.rows == expected || XiangqiRules.transition(expected, after.rows, opponent) != null)
                trace.event("verify.after", "sample=$sample accepted=$valid")
                if (valid) {
                    val verified = session.observe(expected).observe(after.rows)
                    check(after.turnCue == null || after.turnCue.sideToMove == verified.side)
                    _xiangqiRecognition.update { it.copy(result = after, session = verified, detectedSide = verified.side,
                        historyError = null, analysis = null, moveVerified = true, inputUnverified = false, boardChanged = false) }
                    val totalMs = SystemClock.uptimeMillis() - started
                    trace.event("complete", "verified=true inputMs=$inputMs totalMs=$totalMs withinTwoSeconds=${totalMs <= 2000}")
                    return
                }
                delay(150)
            }
            error("Input was not visually confirmed")
        } finally {
            _xiangqiRecognition.update { it.copy(isExecutingMove = false,
                analysis = it.analysis.takeUnless { _ -> it.inputUnverified }) }
        }
    }

    private fun startXiangqiHintWatcher() {
        xiangqiHintWatchJob?.cancel()
        if (_xiangqiRecognition.value.analysis == null || _xiangqiRecognition.value.inputUnverified) return
        xiangqiHintWatchJob = viewModelScope.launch {
            val trace = XiangqiTrace("hint-watch")
            var changedSamples = 0
            while (_xiangqiRecognition.value.analysis != null) {
                delay(650)
                val mode = appSettingsManager.runMode.value
                val pkg = Packages["Xiangqi"]
                if (mode == RunMode.FOREGROUND && (!overlayController.canObserveXiangqi() ||
                    pkg == null || !AccessibilityHelperService.isGameInForeground(pkg))) continue
                val before = _xiangqiRecognition.value.result ?: break
                try {
                    val reference = captureXiangqiReference(mode, pkg, before)
                    val unchanged = before.boardImage?.matches(reference.first) == true
                    changedSamples = if (unchanged) 0 else changedSamples + 1
                    if (changedSamples < 2) continue
                    trace.event("board.changed", "recommendationHidden=true")
                    _xiangqiRecognition.update { it.copy(analysis = null, boardChanged = true) }
                    overlayController.setXiangqiCaptureActive(true)
                    try {
                        delay(150)
                        acceptXiangqiObservation(captureAndRecognizeXiangqi(mode, pkg, trace), trace)
                    } finally { overlayController.setXiangqiCaptureActive(false) }
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    trace.failure("watch", error)
                    _xiangqiRecognition.update { it.copy(analysis = null, boardChanged = true) }
                    break
                }
            }
        }
    }

    // ==================== Dialog ====================

    private fun showDialog(dialog: PanelDialogUiState) {
        _state.update { it.copy(dialog = dialog) }
    }

    fun onDialogDismiss() {
        pendingStart = null
        _state.update { it.copy(dialog = null) }
    }

    fun onDialogConfirm() {
        when (state.value.dialog?.confirmAction) {
            PanelDialogConfirmAction.DISMISS_ONLY -> {
                onDialogDismiss()
            }

            PanelDialogConfirmAction.CONFIRM_PENDING_START -> {
                val pending = pendingStart
                _state.update { it.copy(dialog = null) }
                pendingStart = null
                if (pending != null) {
                    launchManualStart(pending.context)
                }
            }

            PanelDialogConfirmAction.GO_LOG -> {
                onTabChange(PanelTab.LOG)
                onDialogDismiss()
            }

            PanelDialogConfirmAction.GO_LOG_AND_STOP -> {
                onTabChange(PanelTab.LOG)
                onDialogDismiss()
                viewModelScope.launch {
                    compositionService.stop()
                }
            }

            null -> Unit
        }
    }

    override fun onCleared() {
        xiangqiJob?.cancel()
        xiangqiHintWatchJob?.cancel()
        touchPreviewController.onClear()
        pikafishAnalyzer.close()
        super.onCleared()
    }
}
