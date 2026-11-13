package com.aliothmoon.maameow.domain.xiangqi

/** Empty cells need an observed legal continuation plus an independently detected move marker. */
internal fun confirmEmptySquareFromObservedMove(result: XiangqiRecognitionResult, previous: XiangqiGameSession?): XiangqiRecognitionResult {
    val cue = result.turnCue ?: return result
    if (cue.originWhiteRatio !in 0.08f..0.65f) return result
    val square = result.uncertainSquares.singleOrNull() ?: return result
    if (previous == null || result.problemCode != null || result.rows[square.row][square.col] != '.') return result
    if (square != cue.lastMove.from && previous.rows[square.row][square.col] != '.') return result
    val connected = runCatching { previous.observe(result.rows) }.getOrNull() ?: return result
    if (connected.side != cue.sideToMove ||
        (connected !== previous && connected.moves.lastOrNull() != cue.lastMove.iccs)) return result
    return result.copy(fenBoard = XiangqiRules.board(result.rows), uncertainSquares = emptyList(),
        markerConfirmedEmpty = square)
}

internal fun isObservedNewOpening(before: XiangqiRecognitionResult, after: XiangqiRecognitionResult): Boolean =
    before.isComplete && after.isComplete && before.fenBoard != XiangqiRules.START_BOARD &&
        after.fenBoard == XiangqiRules.START_BOARD && after.turnCue == null && before.calibration == after.calibration
