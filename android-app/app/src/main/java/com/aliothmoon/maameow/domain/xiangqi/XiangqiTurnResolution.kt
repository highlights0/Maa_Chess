package com.aliothmoon.maameow.domain.xiangqi

internal data class XiangqiTurnResolution(val side: Char?, val defaultRed: Boolean = false, val conflict: Boolean = false)

internal fun resolveXiangqiTurn(result: XiangqiRecognitionResult, previous: XiangqiGameSession?, wasDefaultRed: Boolean): XiangqiTurnResolution {
    if (!result.isComplete) return XiangqiTurnResolution(null)
    val connected = previous?.let { runCatching { it.observe(result.rows) }.getOrNull() }
    val cue = result.turnCue
    if (cue != null) {
        if (connected != null && cue.sideToMove != connected.side && !wasDefaultRed) {
            return XiangqiTurnResolution(null, conflict = true)
        }
        return XiangqiTurnResolution(cue.sideToMove)
    }
    if (XiangqiGameSession.opening(result.rows) != null) return XiangqiTurnResolution('w')
    if (wasDefaultRed && previous != null && result.rows != previous.rows) {
        // A single observed move identifies its side; two commuting moves do not identify their order.
        for (side in "wb") if (XiangqiRules.transition(previous.rows, result.rows, side) != null) {
            return XiangqiTurnResolution(if (side == 'w') 'b' else 'w')
        }
    }
    if (connected != null) return XiangqiTurnResolution(connected.side, defaultRed = wasDefaultRed)
    // User convention for puzzle/random starting boards without a last-move marker.
    return XiangqiTurnResolution('w', defaultRed = true)
}
