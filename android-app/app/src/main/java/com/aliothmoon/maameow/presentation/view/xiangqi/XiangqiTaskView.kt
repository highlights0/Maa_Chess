package com.aliothmoon.maameow.presentation.view.xiangqi

import android.content.Intent
import android.graphics.Paint as AndroidPaint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.xiangqi.XiangqiModelStatus
import com.aliothmoon.maameow.domain.xiangqi.XiangqiNanoV3Recognizer
import com.aliothmoon.maameow.domain.xiangqi.XiangqiMove
import com.aliothmoon.maameow.domain.xiangqi.xiangqiPieceName
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.presentation.components.MaaWindowInsets
import com.aliothmoon.maameow.presentation.state.PreviewPointerSlots
import com.aliothmoon.maameow.presentation.view.background.viewToVirtualDisplay
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.remote.AppAliveStatus
import com.aliothmoon.maameow.service.AccessibilityHelperService
import org.koin.compose.koinInject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val XIANGQI_CLIENT_TYPE = "Xiangqi"

@Composable
fun XiangqiTaskView(
    viewModel: BackgroundTaskViewModel,
    isActivePage: () -> Boolean = { true },
    floatingPanel: Boolean = false,
) {
    val appSettings: AppSettingsManager = koinInject()
    val compositionService: MaaCompositionService = koinInject()
    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val displayResolution by compositionService.displayResolution.collectAsStateWithLifecycle()
    val gameStatus by viewModel.gameStatus.collectAsStateWithLifecycle()
    val gameOnDisplay by viewModel.gameOnDisplay.collectAsStateWithLifecycle()
    val isSwitching by viewModel.gameSwitchBusy.collectAsStateWithLifecycle()
    val previewSurfaceEpoch by viewModel.previewSurfaceEpoch.collectAsStateWithLifecycle()
    val taskState by viewModel.state.collectAsStateWithLifecycle()
    val modelStatus by viewModel.xiangqiModelStatus.collectAsStateWithLifecycle()
    val recognition by viewModel.xiangqiRecognition.collectAsStateWithLifecycle()
    val accessibilityConnected by AccessibilityHelperService.isConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedScene by rememberSaveable { mutableStateOf("standard") }
    var selectedSide by rememberSaveable { mutableStateOf<String?>(null) }
    var freshPositionConfirmed by rememberSaveable { mutableStateOf(false) }
    var offlineMoveConfirmed by rememberSaveable { mutableStateOf(false) }
    var showBoardDetails by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(recognition.result, recognition.detectedSide) {
        selectedSide = recognition.detectedSide?.toString()
        freshPositionConfirmed = false
        offlineMoveConfirmed = false
    }
    LaunchedEffect(Unit) {
        viewModel.refreshGameStatus(XIANGQI_CLIENT_TYPE)
    }

    val previewResolution = if (
        displayResolution.width == DefaultDisplayConfig.WIDTH &&
        displayResolution.height == DefaultDisplayConfig.HEIGHT
    ) {
        DefaultDisplayConfig.RES_XIANGQI
    } else {
        displayResolution
    }

    if (!floatingPanel && taskState.isFullscreenMonitor) {
        BackHandler { viewModel.onToggleFullscreenMonitor() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(previewResolution.width.toFloat() / previewResolution.height)
                    .pointerInput(previewResolution) {
                        val slots = PreviewPointerSlots()
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    for (change in event.changes) {
                                        val pointerId = change.id.value
                                        val down = change.changedToDownIgnoreConsumed()
                                        val up = change.changedToUpIgnoreConsumed()
                                        if (!down && !up && !change.positionChangedIgnoreConsumed()) continue

                                        val point = viewToVirtualDisplay(change.position, size, previewResolution)
                                        val (x, y) = point.offset
                                        when {
                                            down -> {
                                                if (!point.inside) continue
                                                val contact = slots.acquire(pointerId)
                                                if (contact < 0) continue
                                                viewModel.onTouchDown(x, y, contact)
                                            }
                                            up -> {
                                                val contact = slots.release(pointerId)
                                                if (contact < 0) continue
                                                viewModel.onTouchUp(x, y, contact)
                                            }
                                            else -> {
                                                val contact = slots.indexOf(pointerId)
                                                if (contact < 0) continue
                                                viewModel.onTouchMove(x, y, contact)
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        } finally {
                            viewModel.onTouchCancel()
                        }
                    },
            ) {
                XiangqiDisplaySurface(
                    viewModel = viewModel,
                    resolution = previewResolution,
                    surfaceEpoch = previewSurfaceEpoch,
                    active = isActivePage(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = { viewModel.onToggleFullscreenMonitor() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.task_close_preview_cd),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        return
    }

    val statusText = when {
        isSwitching -> stringResource(R.string.xiangqi_status_busy)
        gameOnDisplay -> stringResource(R.string.xiangqi_status_background)
        gameStatus == AppAliveStatus.ALIVE -> stringResource(R.string.xiangqi_status_running)
        gameStatus == AppAliveStatus.DEAD -> stringResource(R.string.xiangqi_status_stopped)
        else -> stringResource(R.string.xiangqi_status_unknown)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .then(if (floatingPanel) Modifier else Modifier.windowInsetsPadding(MaaWindowInsets.topBar)),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!floatingPanel) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.xiangqi_task_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.xiangqi_task_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!floatingPanel) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_game_package_xiangqi), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { viewModel.refreshGameStatus(XIANGQI_CLIENT_TYPE) },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (gameOnDisplay || gameStatus == AppAliveStatus.ALIVE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = "com.tencent.qqgame.xq",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedScene == "standard",
                            onClick = { selectedScene = "standard" },
                            label = { Text(stringResource(R.string.xiangqi_scene_standard)) },
                        )
                        FilterChip(
                            selected = selectedScene == "puzzle",
                            onClick = { selectedScene = "puzzle" },
                            label = { Text(stringResource(R.string.xiangqi_scene_puzzle)) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isSwitching,
                            onClick = { viewModel.startGame(XIANGQI_CLIENT_TYPE) },
                        ) {
                            if (isSwitching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.xiangqi_action_start))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !isSwitching && gameStatus == AppAliveStatus.ALIVE,
                            onClick = { viewModel.closeGame(XIANGQI_CLIENT_TYPE) },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.xiangqi_action_close))
                        }
                    }
                }
            }
        }

        if (!floatingPanel) item {
            if (runMode == RunMode.BACKGROUND) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.xiangqi_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Card(
                        onClick = { viewModel.onToggleFullscreenMonitor() },
                        modifier = Modifier
                            .width(225.dp)
                            .height(400.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                    ) {
                        XiangqiDisplaySurface(
                            viewModel = viewModel,
                            resolution = previewResolution,
                            surfaceEpoch = previewSurfaceEpoch,
                            active = isActivePage(),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    Text(
                        text = stringResource(R.string.xiangqi_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.xiangqi_foreground_hint),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!floatingPanel) Text(
                        text = stringResource(R.string.xiangqi_recognition_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val modelStatusText = when (val status = modelStatus) {
                        XiangqiModelStatus.Checking -> stringResource(R.string.xiangqi_model_checking)
                        XiangqiModelStatus.Missing -> stringResource(R.string.xiangqi_model_missing)
                        XiangqiModelStatus.Ready -> stringResource(R.string.xiangqi_model_ready)
                        is XiangqiModelStatus.Downloading -> stringResource(
                            R.string.xiangqi_model_downloading,
                            (status.downloadedBytes / 1_000_000L).toInt(),
                            (status.totalBytes / 1_000_000L).toInt(),
                        )
                        is XiangqiModelStatus.Failed -> stringResource(
                            R.string.xiangqi_model_failed,
                            stringResource(R.string.xiangqi_recognition_error_generic),
                        )
                    }
                    if (!floatingPanel || modelStatus != XiangqiModelStatus.Ready) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = modelStatusText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (modelStatus is XiangqiModelStatus.Downloading ||
                            modelStatus is XiangqiModelStatus.Checking
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    if (modelStatus != XiangqiModelStatus.Ready) {
                        OutlinedButton(
                            enabled = modelStatus !is XiangqiModelStatus.Downloading &&
                                modelStatus !is XiangqiModelStatus.Checking,
                            onClick = { viewModel.onDownloadXiangqiModel() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.xiangqi_model_download))
                        }
                    }
                    Button(
                        enabled = modelStatus == XiangqiModelStatus.Ready &&
                            ((runMode == RunMode.BACKGROUND && gameOnDisplay) ||
                                (runMode == RunMode.FOREGROUND &&
                                    accessibilityConnected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)) &&
                            !recognition.isRecognizing && !recognition.isAnalyzing &&
                            !recognition.isExecutingMove,
                        onClick = { viewModel.onRecognizeXiangqiBoard() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (recognition.isRecognizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (recognition.isRecognizing) R.string.xiangqi_recognition_running
                                else R.string.xiangqi_recognition_action,
                            ),
                        )
                    }
                    if (runMode == RunMode.FOREGROUND && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Text(stringResource(R.string.xiangqi_recognition_foreground_android11))
                    } else if (runMode == RunMode.FOREGROUND && !accessibilityConnected) {
                        Text(
                            text = stringResource(R.string.xiangqi_recognition_foreground_accessibility),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.xiangqi_recognition_open_accessibility))
                        }
                    } else if (runMode == RunMode.BACKGROUND && !gameOnDisplay) {
                        Text(
                            text = stringResource(R.string.xiangqi_recognition_need_background),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    recognition.error?.let {
                        Text(
                            text = stringResource(R.string.xiangqi_recognition_error, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    recognition.result?.let { result ->
                        val displayedAnalysis = recognition.analysis?.takeIf {
                            (freshPositionConfirmed || recognition.session != null) &&
                                recognition.historyError == null && selectedSide == it.side.toString()
                        }
                        val suggestedMove = displayedAnalysis?.let {
                            XiangqiMove.parse(it.bestMove, result.rows, it.side)
                        }
                        Text(
                            text = if (result.isComplete) {
                                stringResource(R.string.xiangqi_recognition_complete)
                            } else {
                                stringResource(
                                    R.string.xiangqi_recognition_incomplete,
                                    result.uncertainSquares.size,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.isComplete) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        if (floatingPanel) TextButton(onClick = { showBoardDetails = !showBoardDetails }) {
                            Text(stringResource(if (showBoardDetails) R.string.xiangqi_panel_less else R.string.xiangqi_panel_more))
                        }
                        if (!floatingPanel || showBoardDetails) {
                        XiangqiBoardPreview(
                            rows = result.rows,
                            redAtTopOnScreen = result.redAtTopOnScreen,
                            move = suggestedMove,
                        )
                        Text(
                            text = stringResource(R.string.xiangqi_recognition_piece_legend),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        result.fenBoard?.let { fen ->
                            Text(
                                text = stringResource(R.string.xiangqi_recognition_fen, fen),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        result.problemCode?.let { problemCode ->
                            val problemText = when (problemCode) {
                                XiangqiNanoV3Recognizer.PROBLEM_GENERAL_COUNT ->
                                    stringResource(R.string.xiangqi_recognition_generals_missing)
                                else -> stringResource(R.string.xiangqi_recognition_error_generic)
                            }
                            Text(
                                text = problemText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = stringResource(R.string.xiangqi_recognition_confidence, result.meanConfidence * 100f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                        if (!floatingPanel || showBoardDetails) Text(
                            text = stringResource(R.string.xiangqi_recognition_move_guard),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (result.isComplete) {
                            Text(stringResource(R.string.xiangqi_player_side,
                                stringResource(if (result.playerSide == 'w') R.string.xiangqi_analysis_red else R.string.xiangqi_analysis_black)),
                                style = MaterialTheme.typography.bodySmall)
                            result.turnCue?.let { cue ->
                                Text(stringResource(R.string.xiangqi_visual_turn,
                                    stringResource(if (cue.sideToMove == 'w') R.string.xiangqi_analysis_red else R.string.xiangqi_analysis_black)),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = stringResource(R.string.xiangqi_analysis_side),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedSide == "w",
                                    enabled = recognition.session == null,
                                    onClick = { selectedSide = "w" },
                                    label = { Text(stringResource(R.string.xiangqi_analysis_red)) },
                                )
                                FilterChip(
                                    selected = selectedSide == "b",
                                    enabled = recognition.session == null,
                                    onClick = { selectedSide = "b" },
                                    label = { Text(stringResource(R.string.xiangqi_analysis_black)) },
                                )
                            }
                            if (selectedSide != null && selectedSide != result.playerSide.toString()) {
                                Text(stringResource(R.string.xiangqi_wait_opponent), style = MaterialTheme.typography.bodySmall)
                            }
                            if (recognition.session == null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = freshPositionConfirmed,
                                    onCheckedChange = { freshPositionConfirmed = it },
                                )
                                Text(
                                    text = stringResource(R.string.xiangqi_analysis_fresh_position),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            recognition.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (recognition.historyError != null) {
                                OutlinedButton(onClick = viewModel::onResetXiangqiHistory) {
                                    Text(stringResource(R.string.xiangqi_history_reset))
                                }
                            }
                            if (!viewModel.xiangqiEngineSupported) {
                                Text(
                                    text = stringResource(R.string.xiangqi_analysis_arm64_only),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Button(
                                enabled = selectedSide == result.playerSide.toString() && (freshPositionConfirmed || recognition.session != null) &&
                                    recognition.historyError == null &&
                                    viewModel.xiangqiEngineSupported && !recognition.isAnalyzing &&
                                    !recognition.isExecutingMove,
                                onClick = { selectedSide?.singleOrNull()?.let { viewModel.onAnalyzeXiangqiBoard(it, freshPositionConfirmed) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(
                                    if (recognition.isAnalyzing) R.string.xiangqi_analysis_running
                                    else R.string.xiangqi_analysis_action,
                                ))
                            }
                            recognition.analysisError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            displayedAnalysis?.let { analysis ->
                                Text(
                                    text = stringResource(R.string.xiangqi_analysis_bestmove, analysis.bestMove),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (floatingPanel) Text(stringResource(R.string.xiangqi_game_hint_help), style = MaterialTheme.typography.bodySmall)
                                if (!floatingPanel || showBoardDetails) Text(
                                    text = stringResource(
                                        R.string.xiangqi_analysis_details,
                                        analysis.depth?.toString() ?: "?",
                                        analysis.score ?: "?",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if ((!floatingPanel || showBoardDetails) && analysis.variation.isNotEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.xiangqi_analysis_variation,
                                            analysis.variation.take(8).joinToString(" "),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (suggestedMove != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = offlineMoveConfirmed,
                                            onCheckedChange = { offlineMoveConfirmed = it },
                                        )
                                        Text(
                                            stringResource(R.string.xiangqi_move_offline_confirm),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Button(
                                        enabled = offlineMoveConfirmed && analysis.side == result.playerSide && !recognition.isExecutingMove,
                                        onClick = {
                                            viewModel.onExecuteXiangqiMove(
                                                offlineMoveConfirmed,
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(
                                            if (recognition.isExecutingMove) R.string.xiangqi_move_running
                                            else R.string.xiangqi_move_action,
                                        ))
                                    }
                                    if (analysis.side != result.playerSide) {
                                        Text(stringResource(R.string.xiangqi_wait_opponent), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            recognition.moveError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            if (recognition.moveVerified) {
                                Text(stringResource(R.string.xiangqi_move_verified))
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.xiangqi_task_note_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.xiangqi_task_note_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun XiangqiDisplaySurface(
    viewModel: BackgroundTaskViewModel,
    resolution: DefaultDisplayConfig.Resolution,
    surfaceEpoch: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var lastSentSurface by remember { mutableStateOf<Surface?>(null) }
    val currentResolution by rememberUpdatedState(resolution)
    val currentActive by rememberUpdatedState(active)

    DisposableEffect(viewModel) {
        onDispose {
            if (lastSentSurface != null) {
                lastSentSurface = null
                viewModel.onSurfaceDestroyed()
            }
        }
    }

    key(surfaceEpoch) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.setFormat(PixelFormat.RGBA_8888)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val target = currentResolution
                            holder.setFixedSize(target.width, target.height)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            val target = currentResolution
                            if (
                                width == target.width &&
                                height == target.height &&
                                holder.surface.isValid &&
                                lastSentSurface != holder.surface
                            ) {
                                lastSentSurface = holder.surface
                                viewModel.onSurfaceAvailable(holder.surface)
                            }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            if (lastSentSurface != null) {
                                lastSentSurface = null
                                viewModel.onSurfaceDestroyed()
                            }
                        }
                    })
                }
            },
            modifier = modifier,
            update = { view ->
                view.visibility = if (currentActive) View.VISIBLE else View.INVISIBLE
                val target = currentResolution
                val frame = view.holder.surfaceFrame
                if (
                    view.holder.surface.isValid &&
                    (frame.width() != target.width || frame.height() != target.height)
                ) {
                    view.holder.setFixedSize(target.width, target.height)
                }
            },
        )
    }
}


@Composable
private fun XiangqiBoardPreview(
    rows: List<String>,
    redAtTopOnScreen: Boolean,
    move: XiangqiMove?,
) {
    val displayRows = if (redAtTopOnScreen) {
        rows.asReversed().map { it.reversed() }
    } else {
        rows
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(8f / 9f)
            .padding(horizontal = 8.dp),
    ) {
        val margin = 16.dp.toPx()
        val left = margin
        val right = size.width - margin
        val top = margin
        val bottom = size.height - margin
        val cellWidth = (right - left) / 8f
        val cellHeight = (bottom - top) / 9f
        val lineColor = Color(0xFF725334)
        val boardColor = Color(0xFFF1D9A9)
        val redColor = Color(0xFFB3261E)
        val blackColor = Color(0xFF242424)
        val strokeWidth = 1.25.dp.toPx()

        drawRect(boardColor)
        for (row in 0..9) {
            val y = top + row * cellHeight
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(left, y),
                end = androidx.compose.ui.geometry.Offset(right, y),
                strokeWidth = strokeWidth,
            )
        }
        for (col in 0..8) {
            val x = left + col * cellWidth
            if (col == 0 || col == 8) {
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x, top),
                    end = androidx.compose.ui.geometry.Offset(x, bottom),
                    strokeWidth = strokeWidth,
                )
            } else {
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x, top),
                    end = androidx.compose.ui.geometry.Offset(x, top + 4 * cellHeight),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x, top + 5 * cellHeight),
                    end = androidx.compose.ui.geometry.Offset(x, bottom),
                    strokeWidth = strokeWidth,
                )
            }
        }

        // 九宫斜线
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(left + 3 * cellWidth, top),
            end = androidx.compose.ui.geometry.Offset(left + 5 * cellWidth, top + 2 * cellHeight),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(left + 5 * cellWidth, top),
            end = androidx.compose.ui.geometry.Offset(left + 3 * cellWidth, top + 2 * cellHeight),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(left + 3 * cellWidth, top + 7 * cellHeight),
            end = androidx.compose.ui.geometry.Offset(left + 5 * cellWidth, bottom),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(left + 5 * cellWidth, top + 7 * cellHeight),
            end = androidx.compose.ui.geometry.Offset(left + 3 * cellWidth, bottom),
            strokeWidth = strokeWidth,
        )

        fun squareCenter(row: Int, col: Int): Offset {
            val displayRow = if (redAtTopOnScreen) 9 - row else row
            val displayCol = if (redAtTopOnScreen) 8 - col else col
            return Offset(left + displayCol * cellWidth, top + displayRow * cellHeight)
        }
        val from = move?.let { squareCenter(it.from.row, it.from.col) }
        val to = move?.let { squareCenter(it.to.row, it.to.col) }
        if (from != null && to != null) {
            val angle = atan2(to.y - from.y, to.x - from.x)
            val radius = minOf(cellWidth, cellHeight) * 0.40f
            val arrowEnd = Offset(
                to.x - cos(angle) * radius * 1.25f,
                to.y - sin(angle) * radius * 1.25f,
            )
            drawLine(Color(0xFF167D45), from, arrowEnd, strokeWidth = 4.dp.toPx())
            for (wing in listOf(-0.6f, 0.6f)) {
                drawLine(
                    Color(0xFF167D45),
                    arrowEnd,
                    Offset(
                        arrowEnd.x - cos(angle + wing) * radius * 0.55f,
                        arrowEnd.y - sin(angle + wing) * radius * 0.55f,
                    ),
                    strokeWidth = 4.dp.toPx(),
                )
            }
        }

        drawIntoCanvas { canvas ->
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                textAlign = AndroidPaint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val radius = minOf(cellWidth, cellHeight) * 0.40f
            for (row in 0 until 10) {
                val boardRow = displayRows.getOrNull(row) ?: continue
                for (col in 0 until 9) {
                    val symbol = boardRow.getOrNull(col) ?: '.'
                    if (symbol == '.') continue
                    val centerX = left + col * cellWidth
                    val centerY = top + row * cellHeight
                    val pieceColor = when {
                        symbol == 'x' -> Color(0xFF757575)
                        symbol.isUpperCase() -> redColor
                        else -> blackColor
                    }
                    drawCircle(
                        color = Color(0xFFFFF0D3),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    )
                    drawCircle(
                        color = pieceColor,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                        style = Stroke(width = 1.8.dp.toPx()),
                    )
                    paint.color = pieceColor.toArgb()
                    paint.textSize = radius * 1.18f
                    val metrics = paint.fontMetrics
                    val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
                    val glyph = xiangqiPieceName(symbol)
                    canvas.nativeCanvas.drawText(glyph, centerX, baseline, paint)
                }
            }
        }
        if (from != null && to != null) {
            val radius = minOf(cellWidth, cellHeight) * 0.46f
            drawCircle(Color(0xFFFFA000), radius, from, style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color(0xFF167D45), radius, to, style = Stroke(width = 3.dp.toPx()))
        }
    }
}
