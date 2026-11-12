package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.domain.xiangqi.XiangqiRecognitionResult
import com.aliothmoon.maameow.domain.xiangqi.XiangqiAnalysis

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
)
