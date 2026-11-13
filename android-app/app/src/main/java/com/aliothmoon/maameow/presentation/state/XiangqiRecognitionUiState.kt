package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.domain.xiangqi.XiangqiRecognitionResult
import com.aliothmoon.maameow.domain.xiangqi.XiangqiAnalysis
import com.aliothmoon.maameow.domain.xiangqi.XiangqiGameSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal fun requestXiangqiStop(
    job: Job,
    state: MutableStateFlow<XiangqiRecognitionUiState>,
    stoppedMessage: String,
    unverifiedMessage: String,
) {
    // Main.immediate may finish cancellation synchronously; publish before its finally clears this flag.
    state.update { it.copy(isAutoPlaying = false, isStoppingAutomation = true,
        automationStatus = stoppedMessage,
        moveError = if (it.inputUnverified) unverifiedMessage else it.moveError) }
    job.cancel()
}

internal fun correctAssumedXiangqiTurn(state: XiangqiRecognitionUiState, side: Char): XiangqiRecognitionUiState? {
    val result = state.result?.takeIf { it.isComplete } ?: return null
    if (side !in "wb" || state.inputUnverified || state.isAutoPlaying || state.isStoppingAutomation ||
        state.isRecognizing || state.isAnalyzing || state.isExecutingMove ||
        (state.detectedSide != null && !state.defaultRedTurn) ||
        (result.turnCue != null && result.turnCue.sideToMove != side)) return null
    return state.copy(session = XiangqiGameSession.snapshot(result.fenBoard!!, side), detectedSide = side,
        defaultRedTurn = false, historyError = null, analysis = null, analysisError = null,
        moveVerified = false, moveError = null, boardChanged = false)
}

data class XiangqiRecognitionUiState(
    val isRecognizing: Boolean = false,
    val result: XiangqiRecognitionResult? = null,
    val error: String? = null,
    val isAnalyzing: Boolean = false,
    val analysis: XiangqiAnalysis? = null,
    val analysisError: String? = null,
    val isExecutingMove: Boolean = false,
    val moveError: String? = null,
    val moveVerified: Boolean = false,
    val session: XiangqiGameSession? = null,
    val detectedSide: Char? = null,
    val defaultRedTurn: Boolean = false,
    val historyError: String? = null,
    val isAutoPlaying: Boolean = false,
    val isAutoHinting: Boolean = false,
    val isStoppingAutomation: Boolean = false,
    val automationStatus: String? = null,
    val inputUnverified: Boolean = false,
    val boardChanged: Boolean = false,
)
