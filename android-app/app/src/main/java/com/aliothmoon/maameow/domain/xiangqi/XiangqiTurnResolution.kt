package com.aliothmoon.maameow.domain.xiangqi

internal data class XiangqiTurnResolution(val side: Char?, val defaultRed: Boolean = false, val conflict: Boolean = false)

internal fun resolveXiangqiTurn(result: XiangqiRecognitionResult, previous: XiangqiGameSession?, wasDefaultRed: Boolean): XiangqiTurnResolution {
    if (!result.isComplete) return XiangqiTurnResolution(null)
    val connected = previous?.let { runCatching { it.observe(result.rows) }.getOrNull() }
    val cue = result.turnCue
    if (cue != null) {
        if (connected != null && cue.sideToMove != connected.side && !(wasDefaultRed && connected === previous)) {
            return XiangqiTurnResolution(null, conflict = true)
        }
        return XiangqiTurnResolution(cue.sideToMove)
    }
    if (XiangqiGameSession.opening(result.rows) != null) return XiangqiTurnResolution('w')
    if (connected != null) return XiangqiTurnResolution(connected.side, defaultRed = wasDefaultRed && connected === previous)
    // User convention for puzzle/random starting boards without a last-move marker.
    return XiangqiTurnResolution('w', defaultRed = true)
}
