package com.aliothmoon.maameow.domain.xiangqi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XiangqiMoveVerificationTest {
    private fun frame(board: String) = XiangqiRecognitionResult(
        XiangqiRules.expand(board), XiangqiBoardCalibration.PHONE_1080X2400, board,
        emptyList(), emptyList(), .95f, false, null)

    private val before = frame("4kab2/4a4/n3b1n2/p1P1p4/8p/3N2p2/P3c1c1P/C3C4/4A4/2BAK1BN1")
    private val move = XiangqiMove.parse("e3i3", before.rows, 'b')!!
    private val expected = frame(XiangqiRules.board(XiangqiRules.apply(before.rows, move)))

    @Test fun unchangedFailedTouchPausesAfterOneRecognitionAndOneFreshCheck() = runTest {
        var captures = 0
        var freshChecks = 0
        try {
            awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
                capture = { captures++; before }, stillUnchanged = { freshChecks++; true })
            fail("Missed input must not be accepted")
        } catch (_: XiangqiMoveNotApplied) { }
        assertEquals(1, captures)
        assertEquals(1, freshChecks)
        assertEquals("e3i3", move.iccs)
        assertEquals('c', before.rows[move.from.row][move.from.col])
    }

    @Test fun lateBoardChangeGetsAnotherObservationInsteadOfBeingDeclaredAMissedMove() = runTest {
        var captures = 0
        val after = awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
            capture = { if (++captures == 1) before else expected }, stillUnchanged = { false })
        assertSame(expected, after)
        assertEquals(2, captures)
    }

    @Test fun expectedMoveAndAQuickLegalOpponentReplyAreAcceptedButConflictingTurnIsNot() = runTest {
        val reply = XiangqiRules.legalMoves(expected.rows, 'w').first()
        val replied = frame(XiangqiRules.board(XiangqiRules.apply(expected.rows, reply)))
            .copy(turnCue = XiangqiTurnCue(reply, 'w', 'b', .2f, .03f))
        for (observed in listOf(expected, replied)) {
            assertSame(observed, awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
                capture = { observed }, stillUnchanged = { error("No fresh unchanged check needed") }))
        }
        try {
            awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
                capture = { replied.copy(turnCue = replied.turnCue!!.copy(sideToMove = 'w')) }, stillUnchanged = { true })
            fail("Conflicting turn must pause")
        } catch (_: IllegalStateException) { }
    }

    @Test fun animationIncompleteAndUnexpectedBoardsAllExhaustThreeAttempts() = runTest {
        val captures = listOf<suspend () -> XiangqiRecognitionResult>(
            { throw XiangqiFrameNotReady("animation") },
            { throw XiangqiCalibrationRequired(720, 1280) },
            { before.copy(uncertainSquares = listOf(move.from)) },
            { expected.copy(redAtTopOnScreen = true) },
        )
        for (capture in captures) {
            var calls = 0
            try {
                awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
                    capture = { calls++; capture() }, stillUnchanged = { error("Unsafe frame cannot confirm unchanged input") })
                fail("Unconfirmed frames must exhaust the budget")
            } catch (error: IllegalStateException) {
                assertTrue(error.message!!.contains("3 captures"))
            }
            assertEquals(3, calls)
        }
    }

    @Test fun slowUnconfirmedCaptureExhaustsTheRetryBudgetWithoutStartingAnotherCapture() = runTest {
        var calls = 0
        try {
            awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
                capture = { calls++; delay(11_000); before.copy(uncertainSquares = listOf(move.from)) },
                stillUnchanged = { false }, clockMillis = { testScheduler.currentTime })
            fail("Timeout must pause")
        } catch (error: IllegalStateException) {
            assertFalse(error is CancellationException)
            assertTrue(error.message!!.contains("10 seconds"))
        }
        assertEquals(1, calls)
    }

    @Test fun aValidResultIsNotDiscardedWhenInferenceFinishesAfterTheRetryDeadline() = runTest {
        var calls = 0
        val after = awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
            capture = {
                if (++calls == 1) {
                    delay(6_000)
                    before.copy(uncertainSquares = listOf(move.from))
                } else {
                    delay(5_000)
                    expected
                }
            }, stillUnchanged = { false }, clockMillis = { testScheduler.currentTime })
        assertSame(expected, after)
        assertEquals(2, calls)
        assertTrue(testScheduler.currentTime > 10_000)
    }

    @Test fun stoppingRemainsCancellationAndAnObservedNewOpeningCanRetirePendingInput() = runTest {
        try {
            awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
                capture = { throw CancellationException("stop") }, stillUnchanged = { false })
            fail("Stop must propagate")
        } catch (_: CancellationException) { }
        val opening = frame(XiangqiRules.START_BOARD)
        assertSame(opening, awaitXiangqiMoveConfirmation(before, move, 'b', XiangqiTrace("test"),
            capture = { opening }, stillUnchanged = { false }))
    }
}
