package com.aliothmoon.maameow.domain.xiangqi

/** Empty cells need a legal observed continuation plus independent marker or unchanged-cell evidence. */
internal fun confirmEmptySquareFromObservedMove(
    result: XiangqiRecognitionResult,
    previous: XiangqiGameSession?,
    observed: XiangqiRecognitionResult? = null,
): XiangqiRecognitionResult {
    val square = result.uncertainSquares.singleOrNull() ?: return result
    if (previous == null || result.problemCode != null || result.rows[square.row][square.col] != '.') return result
    val connected = runCatching { previous.observe(result.rows) }.getOrNull() ?: return result
    val cue = result.turnCue
    if (cue != null && cue.originWhiteRatio !in 0.08f..0.65f) return result
    if (cue != null && (connected.side != cue.sideToMove ||
        (connected !== previous && connected.moves.lastOrNull() != cue.lastMove.iccs))) return result
    if (previous.rows[square.row][square.col] == '.' && observed?.isComplete == true &&
        observed.rows == previous.rows && observed.calibration == result.calibration &&
        observed.redAtTopOnScreen == result.redAtTopOnScreen) {
        val physical = if (result.redAtTopOnScreen) XiangqiSquare(9 - square.row, 8 - square.col) else square
        if (observed.boardImage?.let { old -> result.boardImage?.let { old.sameSquarePixels(it, physical) } } == true) {
            return result.copy(fenBoard = XiangqiRules.board(result.rows), uncertainSquares = emptyList(), imageConfirmedEmpty = square)
        }
    }
    if (cue == null) return result
    if (square != cue.lastMove.from && previous.rows[square.row][square.col] != '.') return result
    return result.copy(fenBoard = XiangqiRules.board(result.rows), uncertainSquares = emptyList(),
        markerConfirmedEmpty = square)
}

internal fun isObservedNewOpening(before: XiangqiRecognitionResult, after: XiangqiRecognitionResult): Boolean =
    before.isComplete && after.isComplete && before.fenBoard != XiangqiRules.START_BOARD &&
        after.fenBoard == XiangqiRules.START_BOARD && after.turnCue == null && before.calibration == after.calibration
