package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test

class XiangqiRulesTest {
    private val opening = XiangqiRules.expand(XiangqiRules.START_BOARD)

    @Test fun openingPerftMatchesPinnedPikafish() {
        val moves = XiangqiRules.legalMoves(opening, 'w')
        assertEquals(44, moves.size)
        assertEquals(1920, moves.sumOf { XiangqiRules.legalMoves(XiangqiRules.apply(opening, it), 'b').size })
        assertFalse(moves.any { it.iccs == "b0d1" })
    }

    @Test fun kingSafetyHorseLegAndCannonScreen() {
        val pinned = XiangqiRules.expand("4k4/9/9/9/4R4/9/9/9/9/4K4")
        assertFalse(XiangqiRules.isLegal(pinned, 'w', XiangqiMove.parse("e5d5", pinned, 'w')!!))
        assertTrue(XiangqiRules.isLegal(opening, 'w', XiangqiMove.parse("b2b9", opening, 'w')!!))
        assertFalse(XiangqiRules.isLegal(opening, 'w', XiangqiMove.parse("b2b7", opening, 'w')!!))
        val blocked = opening.toMutableList().apply { set(8, ".P.......") }
        assertFalse(XiangqiRules.isLegal(blocked, 'w', XiangqiMove.parse("b0c2", blocked, 'w')!!))
    }

    @Test fun historyInfersTurnAndRejectsMissedMoves() {
        val start = XiangqiGameSession.opening(opening)!!
        val first = XiangqiRules.apply(opening, XiangqiMove.parse("b2e2", opening, 'w')!!)
        val session = start.observe(first)
        assertEquals('b', session.side)
        assertEquals(listOf("b2e2"), session.moves)
        assertTrue(session.positionCommand.endsWith(" moves b2e2"))
        assertSame(session, session.observe(first))
        val secondRed = XiangqiRules.apply(first, XiangqiMove.parse("a3a4", first, 'w')!!)
        assertThrows(IllegalStateException::class.java) { session.observe(secondRed) }
        assertEquals(opening, start.rows)
    }

    @Test fun importedCountersAndHistoryArePreserved() {
        val fen = "${XiangqiRules.START_BOARD} w - - 17 9"
        val session = XiangqiGameSession.fromFen(fen, listOf("b2e2", "b9c7"))
        assertEquals(fen, session.initialFen)
        assertEquals('w', session.side)
        assertEquals(listOf("b2e2", "b9c7"), session.moves)
        assertThrows(IllegalArgumentException::class.java) { XiangqiGameSession.fromFen("${XiangqiRules.START_BOARD} w - - ? 1") }
    }

    @Test fun manualMoveAndFastOpponentReplyAreRecoveredTogether() {
        val start = XiangqiGameSession.opening(opening)!!
        val after = XiangqiGameSession.fromFen(start.initialFen, listOf("b2e2", "b9c7")).rows
        val recovered = start.observe(after)
        assertEquals(listOf("b2e2", "b9c7"), recovered.moves)
        assertEquals('w', recovered.side)
    }

    @Test fun newOpeningAutomaticallyReplacesPreviousGame() {
        val old = XiangqiGameSession.fromFen("${XiangqiRules.START_BOARD} w - - 17 9", listOf("b2e2", "b9c7"))
        val fresh = XiangqiGameSession.reconnect(old, opening, 'w')!!
        assertTrue(fresh.historyKnown)
        assertTrue(fresh.moves.isEmpty())
        assertEquals("${XiangqiRules.START_BOARD} w - - 0 1", fresh.initialFen)
    }

    @Test fun changedPuzzleUsesExplicitSearchBaselineWithoutInventingHistory() {
        val puzzle = XiangqiRules.expand("3k5/5P3/3a1N3/9/9/2n6/9/9/9/3K2B2")
        val snapshot = XiangqiGameSession.reconnect(XiangqiGameSession.opening(opening), puzzle, 'w')!!
        assertEquals(puzzle, snapshot.rows)
        assertFalse(snapshot.historyKnown)
        assertEquals('w', snapshot.side)
        assertNull(XiangqiGameSession.reconnect(snapshot, puzzle, null))
    }

    @Test fun connectedBoardsKeepKnownCountersAndMoves() {
        val start = XiangqiGameSession.fromFen("${XiangqiRules.START_BOARD} w - - 17 9")
        val after = XiangqiGameSession.fromFen(start.initialFen, listOf("b2e2", "b9c7")).rows
        val session = XiangqiGameSession.reconnect(start, after, 'w')!!
        assertEquals(start.initialFen, session.initialFen)
        assertEquals(listOf("b2e2", "b9c7"), session.moves)
        assertTrue(session.historyKnown)
    }
}
