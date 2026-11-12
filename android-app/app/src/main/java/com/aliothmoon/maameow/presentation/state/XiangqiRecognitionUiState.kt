package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.domain.xiangqi.XiangqiRecognitionResult

data class XiangqiRecognitionUiState(
    val isRecognizing: Boolean = false,
    val result: XiangqiRecognitionResult? = null,
    val error: String? = null,
)
