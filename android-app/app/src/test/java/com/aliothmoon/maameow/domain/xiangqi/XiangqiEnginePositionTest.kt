package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XiangqiEnginePositionTest {
    private val start = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR"

    @Test fun freshBoardAndMove() {
        assertEquals("$start w - - 0 1", XiangqiEnginePosition.freshFen(start, 'w'))
        XiangqiEnginePosition.requireMoveOnBoard(start, 'w', "h2e2")
        assertThrows(IllegalArgumentException::class.java) {
            XiangqiEnginePosition.requireMoveOnBoard(start, 'b', "h2e2")
        }
    }

    @Test fun facingGeneralsAndBadRanksAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            XiangqiEnginePosition.freshFen("4k4/9/9/9/9/9/9/9/9/4K4", 'w')
        }
        assertThrows(IllegalArgumentException::class.java) {
            XiangqiEnginePosition.freshFen("${start.substringBefore('/')}/9", 'w')
        }
    }
}
