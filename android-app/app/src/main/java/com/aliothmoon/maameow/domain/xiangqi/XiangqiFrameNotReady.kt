package com.aliothmoon.maameow.domain.xiangqi

internal class XiangqiFrameNotReady(message: String) : IllegalStateException(message)

internal fun canRescanXiangqiBeforeInput(error: Exception, inputPending: Boolean): Boolean =
    error is XiangqiFrameNotReady && !inputPending
