package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XiangqiMoveTest {
    private val opening = listOf(
        "rnbakabnr", ".........", ".c.....c.", "p.p.p.p.p",
        ".........", ".........", "P.P.P.P.P", ".C.....C.",
        ".........", "RNBAKABNR",
    )

    @Test fun mapsIccsRanksToImageRows() {
        assertEquals(
            XiangqiMove(XiangqiSquare(7, 1), XiangqiSquare(7, 4)),
            XiangqiMove.parse("b2e2", opening, 'w'),
        )
        assertEquals("炮", xiangqiPieceName('C'))
        assertEquals("卒", xiangqiPieceName('p'))
        assertEquals("帥", xiangqiPieceName('K'))
        assertEquals("將", xiangqiPieceName('k'))
        assertEquals(
            XiangqiScreenPoint(162, 1035),
            XiangqiSquare(7, 1).screenPoint(XiangqiBoardCalibration.MUMU_900X1600, false),
        )
        assertEquals(
            XiangqiScreenPoint(738, 555),
            XiangqiSquare(7, 1).screenPoint(XiangqiBoardCalibration.MUMU_900X1600, true),
        )
    }

    @Test fun rejectsWrongSideAndMalformedMoves() {
        assertNull(XiangqiMove.parse("b2e2", opening, 'b'))
        assertNull(XiangqiMove.parse("b2b2", opening, 'w'))
        assertNull(XiangqiMove.parse("z2e2", opening, 'w'))
        assertNull(XiangqiMove.parse("b2a0", opening, 'w'))
    }

    @Test fun confirmsOnlyTheObservedPieceAtDestination() {
        val move = XiangqiMove.parse("b2e2", opening, 'w')!!
        val after = opening.toMutableList().apply {
            set(7, "....C..C.")
        }
        assertEquals(true, move.isObservedAfter(opening, after))
        assertEquals(false, move.isObservedAfter(opening, opening))
        assertEquals(false, move.isObservedAfter(opening, after.toMutableList().apply { set(7, "....p..C.") }))
    }

    @Test fun downscaledCaptureCoordinatesReturnToNativeDisplaySize() {
        val capture = XiangqiBoardCalibration.MUMU_900X1600.scaledTo(720, 1280)
        assertEquals(
            XiangqiScreenPoint(162, 1035),
            XiangqiSquare(7, 1).screenPoint(capture.scaledTo(900, 1600), false),
        )
    }
}
