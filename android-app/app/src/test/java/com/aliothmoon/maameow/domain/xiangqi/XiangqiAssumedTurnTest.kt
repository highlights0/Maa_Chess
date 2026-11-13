package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.presentation.state.XiangqiRecognitionUiState
import com.aliothmoon.maameow.presentation.state.correctAssumedXiangqiTurn
import org.junit.Assert.*
import org.junit.Test

class XiangqiAssumedTurnTest {
    private val opening = XiangqiRules.expand(XiangqiRules.START_BOARD)
    private fun move(rows: List<String>, iccs: String, side: Char) = XiangqiRules.apply(rows, XiangqiMove.parse(iccs, rows, side)!!)
    private fun frame(rows: List<String>) = XiangqiRecognitionResult(rows, XiangqiBoardCalibration.MUMU_900X1600,
        XiangqiRules.board(rows), emptyList(), emptyList(), .95f, true, null)

    @Test fun commutingMovesCannotPromoteAnAssumedTurnToConfirmedAndAMarkerCanCorrectIt() {
        val afterPawn = move(opening, "c3c4", 'w')
        val assumed = XiangqiGameSession.snapshot(XiangqiRules.board(afterPawn), 'w')
        val afterBoth = move(move(afterPawn, "b7c7", 'b'), "b0c2", 'w')
        val current = frame(afterBoth)
        val inferred = resolveXiangqiTurn(current, assumed, true)
        assertEquals('w', inferred.side)
        assertTrue(inferred.defaultRed)
        val cue = XiangqiTurnCue(XiangqiMove.parse("b0c2", afterPawn, 'w')!!, 'w', 'b', .2f, .03f)
        val corrected = resolveXiangqiTurn(current.copy(turnCue = cue), assumed, true)
        assertEquals('b', corrected.side)
        assertFalse(corrected.conflict)
        assertFalse(corrected.defaultRed)
        assertTrue(resolveXiangqiTurn(current.copy(turnCue = cue), assumed, false).conflict)
        val blackOnly = resolveXiangqiTurn(frame(move(afterPawn, "b7c7", 'b')), assumed, true)
        assertEquals('w', blackOnly.side)
        assertFalse(blackOnly.defaultRed)
    }

    @Test fun userCanCorrectOnlyAnIdleAssumedOrUnknownTurnWithoutInventingHistory() {
        val afterPawn = frame(move(opening, "c3c4", 'w'))
        val state = XiangqiRecognitionUiState(result = afterPawn, detectedSide = 'w', defaultRedTurn = true)
        val corrected = correctAssumedXiangqiTurn(state, 'b')!!
        assertEquals('b', corrected.detectedSide)
        assertEquals('b', corrected.session!!.side)
        assertFalse(corrected.session.historyKnown)
        assertTrue(corrected.session.moves.isEmpty())
        assertFalse(corrected.defaultRedTurn)
        assertFalse(corrected.moveVerified)
        assertNull(correctAssumedXiangqiTurn(state.copy(isAutoPlaying = true), 'b'))
        assertNull(correctAssumedXiangqiTurn(state.copy(inputUnverified = true), 'b'))
        assertNull(correctAssumedXiangqiTurn(state.copy(defaultRedTurn = false), 'b'))
    }
}
