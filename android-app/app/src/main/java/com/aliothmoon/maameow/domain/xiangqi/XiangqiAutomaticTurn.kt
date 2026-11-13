package com.aliothmoon.maameow.domain.xiangqi

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun runXiangqiAutomaticTurn(
    hintsOnly: Boolean,
    analyze: suspend () -> Unit,
    execute: suspend () -> Unit,
) {
    analyze()
    currentCoroutineContext().ensureActive()
    if (!hintsOnly) execute()
}
