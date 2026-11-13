package com.aliothmoon.maameow.domain.xiangqi

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal class XiangqiMoveNotApplied : IllegalStateException("Board stayed unchanged after touch input")

internal suspend fun awaitXiangqiMoveConfirmation(
    before: XiangqiRecognitionResult,
    move: XiangqiMove,
    side: Char,
    trace: XiangqiTrace,
    capture: suspend () -> XiangqiRecognitionResult,
    stillUnchanged: suspend () -> Boolean,
): XiangqiRecognitionResult {
    val expected = XiangqiRules.apply(before.rows, move)
    val opponent = if (side == 'w') 'b' else 'w'
    return withTimeoutOrNull(10_000L) {
        // Every capture consumes an attempt, including animation and incomplete boards.
        repeat(3) { sample ->
            val after = try { capture() } catch (error: XiangqiFrameNotReady) {
                trace.event("verify.waiting", "sample=$sample reason=animation inputNotRepeated=true")
                delay(150)
                return@repeat
            }
            val sameGeometry = after.calibration == before.calibration &&
                after.redAtTopOnScreen == before.redAtTopOnScreen
            val valid = after.isComplete && sameGeometry &&
                (after.rows == expected || XiangqiRules.transition(expected, after.rows, opponent) != null)
            trace.event("verify.after", "sample=$sample accepted=$valid complete=${after.isComplete} unchanged=${after.rows == before.rows}")
            if (valid) {
                val nextSide = if (after.rows == expected) opponent else side
                check(after.turnCue == null || after.turnCue.sideToMove == nextSide) { "Post-input turn conflicts with observed movement" }
                return@withTimeoutOrNull after
            }
            if (isObservedNewOpening(before, after)) return@withTimeoutOrNull after
            // The model used an earlier screenshot; check a fresh frame before declaring a missed move.
            if (after.isComplete && sameGeometry && after.rows == before.rows && stillUnchanged()) {
                throw XiangqiMoveNotApplied()
            }
            delay(150)
        }
        null
    } ?: error("Input was not visually confirmed within 3 captures / 10 seconds")
}
