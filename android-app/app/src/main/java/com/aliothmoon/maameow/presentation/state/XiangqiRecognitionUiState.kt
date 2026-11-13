package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.domain.xiangqi.XiangqiRecognitionResult
import com.aliothmoon.maameow.domain.xiangqi.XiangqiAnalysis
import com.aliothmoon.maameow.domain.xiangqi.XiangqiGameSession

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
    val historyError: String? = null,
    val isAutoPlaying: Boolean = false,
    val isStoppingAutomation: Boolean = false,
    val automationStatus: String? = null,
    val inputUnverified: Boolean = false,
    val boardChanged: Boolean = false,
)
