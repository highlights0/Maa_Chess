package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class XiangqiTurnCueTest {
    private val calibration = XiangqiBoardCalibration.MUMU_900X1600.scaledTo(450, 800)
    private val opening = XiangqiRules.expand(XiangqiRules.START_BOARD)
    private val redMove = XiangqiMove.parse("h2e2", opening, 'w')!!
    private val afterRed = XiangqiRules.apply(opening, redMove)

    private fun image(move: XiangqiMove, flipped: Boolean = false): IntArray {
        val pixels = IntArray(450 * 800) { 0xffd7ac75.toInt() }
        mark(pixels, move.from, false, flipped)
        mark(pixels, move.to, true, flipped)
        return pixels
    }

    private fun mark(pixels: IntArray, square: XiangqiSquare, halo: Boolean, flipped: Boolean = false) {
        val point = square.screenPoint(calibration, flipped)
        for (y in point.y - 25..point.y + 25) for (x in point.x - 25..point.x + 25) {
            if (x !in 0 until 450 || y !in 0 until 800) continue
            val r = hypot((x - point.x).toDouble(), (y - point.y).toDouble()) / 48
            if (if (halo) r in 0.44..0.48 else r <= 0.1 || r in 0.16..0.21) pixels[y * 450 + x] = -1
        }
    }

    @Test fun redCannonMarkerMeansBlackToMove() {
        val cue = XiangqiTurnCueDetector.detect(image(redMove), 450, 800, calibration, afterRed, false)!!
        assertEquals("h2e2", cue.lastMove.iccs)
        assertEquals('b', cue.sideToMove)
    }

    @Test fun blackElephantMarkerMeansRedToMoveIncludingRotatedBoard() {
        val black = XiangqiMove.parse("g9e7", afterRed, 'b')!!
        val after = XiangqiRules.apply(afterRed, black)
        for (flipped in listOf(false, true)) {
            val cue = XiangqiTurnCueDetector.detect(image(black, flipped), 450, 800, calibration, after, flipped)!!
            assertEquals('w', cue.sideToMove)
            assertEquals(black, cue.lastMove)
        }
    }

    @Test fun ambiguityAndImpossibleLastMoveStayUnknown() {
        val ambiguous = image(redMove)
        mark(ambiguous, XiangqiSquare(9, 0), true)
        assertNull(XiangqiTurnCueDetector.detect(ambiguous, 450, 800, calibration, afterRed, false))
        val impossible = XiangqiMove(XiangqiSquare(1, 0), redMove.to)
        assertNull(XiangqiTurnCueDetector.detect(image(impossible), 450, 800, calibration, afterRed, false))
        assertNull(XiangqiTurnCueDetector.detect(IntArray(450 * 800) { 0xffd7ac75.toInt() }, 450, 800, calibration, afterRed, false))
    }
}
