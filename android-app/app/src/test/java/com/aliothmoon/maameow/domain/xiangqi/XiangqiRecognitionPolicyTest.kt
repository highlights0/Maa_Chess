package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test

class XiangqiRecognitionPolicyTest {
    private val beforeBoard = "r2akab2/4c4/b4c2r/p1p2R2p/4p1p2/9/P1P1P1R1P/2N1C1N2/9/2BAKAB2"
    private val previous = XiangqiGameSession.snapshot(beforeBoard, 'b')
    private val move = XiangqiMove.parse("e8f8", previous.rows, 'b')!!
    private val afterBoard = XiangqiRules.board(XiangqiRules.apply(previous.rows, move))
    private val cue = XiangqiTurnCue(move, 'b', 'w', .1948f, .027f)

    private fun frame(board: String): XiangqiRecognitionResult {
        val rows = XiangqiRules.expand(board)
        return XiangqiRecognitionResult(rows, XiangqiBoardCalibration.PHONE_1080X2400, board,
            rows.flatMapIndexed { row, line -> line.mapIndexed { col, symbol -> XiangqiPiecePrediction(row, col, symbol, .95f) } },
            emptyList(), .95f, false, null)
    }

    private fun uncertainOrigin(): XiangqiRecognitionResult = frame(afterBoard).let { result ->
        result.copy(fenBoard = null, uncertainSquares = listOf(move.from), turnCue = cue,
            predictions = result.predictions.map { if (it.row == move.from.row && it.col == move.from.col) it.copy(confidence = .79946256f) else it })
    }

    @Test fun markedEmptyOriginIsConfirmedAgainstObservedLegalMovementWithoutChangingModelScores() {
        val raw = uncertainOrigin()
        val confirmed = confirmEmptySquareFromObservedMove(raw, previous)
        assertFalse(raw.isComplete)
        assertTrue(confirmed.isComplete)
        assertEquals(move.from, confirmed.markerConfirmedEmpty)
        assertEquals(afterBoard, confirmed.fenBoard)
        assertSame(raw.predictions, confirmed.predictions)
        assertEquals(.79946256f, confirmed.predictions.first { it.row == move.from.row && it.col == move.from.col }.confidence)
        assertEquals(beforeBoard, XiangqiRules.board(previous.rows))
    }

    @Test fun unknownHistoryMissingMarkersMultipleUncertainCellsAndContradictionsRemainBlocked() {
        val raw = uncertainOrigin()
        assertSame(raw, confirmEmptySquareFromObservedMove(raw, null))
        val noMarker = raw.copy(turnCue = null)
        assertSame(noMarker, confirmEmptySquareFromObservedMove(noMarker, previous))
        val covered = raw.copy(turnCue = cue.copy(originWhiteRatio = 1f))
        assertSame(covered, confirmEmptySquareFromObservedMove(covered, previous))
        val multiple = raw.copy(uncertainSquares = listOf(move.from, move.to))
        assertSame(multiple, confirmEmptySquareFromObservedMove(multiple, previous))
        val general = raw.copy(problemCode = XiangqiNanoV3Recognizer.PROBLEM_GENERAL_COUNT)
        assertSame(general, confirmEmptySquareFromObservedMove(general, previous))
        val wrongOrigin = raw.copy(uncertainSquares = listOf(move.to))
        assertSame(wrongOrigin, confirmEmptySquareFromObservedMove(wrongOrigin, previous))
        assertSame(raw, confirmEmptySquareFromObservedMove(raw, previous.copy(side = 'w')))
    }

    @Test fun newPuzzleDefaultsToRedButExistingBlackTurnAndDefaultProvenanceArePreserved() {
        val puzzle = frame("4k4/9/9/9/4p4/9/9/9/9/4K4")
        val initial = resolveXiangqiTurn(puzzle, null, false)
        assertEquals('w', initial.side)
        assertTrue(initial.defaultRed)
        val black = XiangqiGameSession.snapshot(puzzle.fenBoard!!, 'b')
        assertEquals('b', resolveXiangqiTurn(puzzle, black, false).side)
        val red = XiangqiGameSession.snapshot(puzzle.fenBoard, 'w')
        assertTrue(resolveXiangqiTurn(puzzle, red, true).defaultRed)
        val moved = frame(XiangqiRules.board(XiangqiRules.apply(red.rows, XiangqiMove.parse("e0d0", red.rows, 'w')!!)))
        val connected = resolveXiangqiTurn(moved, red, true)
        assertEquals('b', connected.side)
        assertFalse(connected.defaultRed)
    }

    @Test fun aKnownEmptyCellCanRemainConfirmedWhenTheNextLegalMoveMovesTheMarkerAway() {
        val known = previous.observe(XiangqiRules.expand(afterBoard))
        val nextMove = XiangqiMove.parse("f6f5", known.rows, 'w')!!
        assertTrue(XiangqiRules.isLegal(known.rows, 'w', nextMove))
        val next = frame(XiangqiRules.board(XiangqiRules.apply(known.rows, nextMove)))
            .copy(fenBoard = null, uncertainSquares = listOf(move.from),
                turnCue = XiangqiTurnCue(nextMove, 'w', 'b', .2f, .03f),
                predictions = frame(XiangqiRules.board(XiangqiRules.apply(known.rows, nextMove))).predictions.map {
                    if (it.row == move.from.row && it.col == move.from.col) it.copy(confidence = .79946256f) else it
                })
        assertTrue(confirmEmptySquareFromObservedMove(next, known).isComplete)
        val unconnected = next.copy(rows = next.rows.toMutableList().apply { set(5, "R........") })
        assertSame(unconnected, confirmEmptySquareFromObservedMove(unconnected, known))
    }

    @Test fun visualTurnOverridesADefaultButDisagreementWithKnownMovementPauses() {
        val after = frame(afterBoard).copy(turnCue = cue)
        val known = previous.observe(after.rows)
        assertEquals('w', resolveXiangqiTurn(after, previous, false).side)
        val conflict = after.copy(turnCue = cue.copy(sideToMove = 'b'))
        assertTrue(resolveXiangqiTurn(conflict, known, false).conflict)
        assertNull(resolveXiangqiTurn(conflict, known, false).side)
        assertEquals('b', resolveXiangqiTurn(conflict, known, true).side)
        assertNull(resolveXiangqiTurn(uncertainOrigin(), previous, false).side)
    }

    @Test fun aConfirmedNewOpeningCanRetireOldInputButAnUnchangedFailedFirstMoveCannot() {
        val opening = frame(XiangqiRules.START_BOARD)
        assertTrue(isObservedNewOpening(frame(afterBoard), opening))
        assertTrue(isObservedNewOpening(frame(afterBoard), opening.copy(redAtTopOnScreen = true)))
        assertFalse(isObservedNewOpening(opening, opening))
        assertFalse(isObservedNewOpening(frame(afterBoard), opening.copy(turnCue = cue)))
        assertFalse(isObservedNewOpening(frame(afterBoard), opening.copy(uncertainSquares = listOf(move.from))))
        assertFalse(isObservedNewOpening(frame(afterBoard), frame(beforeBoard)))
        val standardTurn = resolveXiangqiTurn(opening, null, false)
        assertEquals('w', standardTurn.side)
        assertFalse(standardTurn.defaultRed)
    }
}
