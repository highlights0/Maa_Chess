package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test

class XiangqiDisplayGeometryTest {
    @Test fun thumbnailMapsToActualLogicalDisplayRegardlessOfPhysicalPhoneResolution() {
        val actual = XiangqiDisplayGeometry.fromWire(intArrayOf(7, 900, 1600, 0))!!
        val grid = XiangqiBoardCalibration(86.0, 391.0, 628.0, 1000.0, 720, 1280)
        val native = actual.map(grid)
        assertEquals(XiangqiScreenPoint(192, 1081), XiangqiSquare(7, 1).screenPoint(native, false))
        assertEquals(XiangqiScreenPoint(446, 1081), XiangqiSquare(7, 4).screenPoint(native, false))
        assertArrayEquals(intArrayOf(7, 900, 1600, 0), actual.toWire())
        assertFalse(actual.contains(900, 1600))
        assertFalse(actual.contains(-1, 0))
    }

    @Test fun absentPrimaryMalformedAndWrongAspectTargetsAreRejected() {
        for (wire in listOf(null, intArrayOf(), intArrayOf(0, 900, 1600, 0),
            intArrayOf(-1, 900, 1600, 0), intArrayOf(2, 0, 1600, 0), intArrayOf(2, 900, 1600, 4))) {
            assertNull(XiangqiDisplayGeometry.fromWire(wire))
        }
        val rotated = XiangqiDisplayGeometry.fromWire(intArrayOf(8, 1600, 900, 1))!!
        assertThrows(IllegalArgumentException::class.java) { rotated.map(XiangqiBoardCalibration.MUMU_900X1600) }
        assertNotEquals(rotated, rotated.copy(displayId = 9))
        assertNotEquals(rotated, rotated.copy(rotation = 3))
    }
}
