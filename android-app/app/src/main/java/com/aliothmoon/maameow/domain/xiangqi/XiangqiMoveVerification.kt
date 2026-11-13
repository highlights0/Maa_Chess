package com.aliothmoon.maameow.domain.xiangqi

import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class XiangqiMoveNotApplied : IllegalStateException("Board stayed unchanged after touch input")

internal suspend fun awaitXiangqiMoveConfirmation(
    before: XiangqiRecognitionResult,
    move: XiangqiMove,
    side: Char,
    trace: XiangqiTrace,
    capture: suspend () -> XiangqiRecognitionResult,
    stillUnchanged: suspend () -> Boolean,
    clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
): XiangqiRecognitionResult {
    val expected = XiangqiRules.apply(before.rows, move)
    val opponent = if (side == 'w') 'b' else 'w'
    val started = clockMillis()
    var attempts = 0
    for (sample in 0 until 3) {
        currentCoroutineContext().ensureActive()
        if (clockMillis() - started >= 10_000) break
        attempts++
        // Limit new retries, but do not discard an in-flight recognition result at the deadline.
        val after = try { capture() } catch (error: XiangqiFrameNotReady) {
            trace.event("verify.waiting", "sample=$sample reason=animation inputNotRepeated=true")
            delay(150)
            continue
        }
        currentCoroutineContext().ensureActive()
        val sameGeometry = after.calibration == before.calibration &&
            after.redAtTopOnScreen == before.redAtTopOnScreen
        val valid = after.isComplete && sameGeometry &&
            (after.rows == expected || XiangqiRules.transition(expected, after.rows, opponent) != null)
        trace.event("verify.after", "sample=$sample accepted=$valid complete=${after.isComplete} unchanged=${after.rows == before.rows} verificationMs=${clockMillis() - started}")
        if (valid) {
            val nextSide = if (after.rows == expected) opponent else side
            check(after.turnCue == null || after.turnCue.sideToMove == nextSide) { "Post-input turn conflicts with observed movement" }
            return after
        }
        if (isObservedNewOpening(before, after)) return after
        // The model used an earlier screenshot; check a fresh frame before declaring a missed move.
        if (after.isComplete && sameGeometry && after.rows == before.rows && stillUnchanged()) {
            throw XiangqiMoveNotApplied()
        }
        delay(150)
    }
    trace.event("verify.exhausted", "attempts=$attempts verificationMs=${clockMillis() - started} inputNotRepeated=true")
    error("Input was not visually confirmed after 3 captures / 10 seconds retry budget")
}
