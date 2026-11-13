package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.overlay.XiangqiStatusOverlay
import com.aliothmoon.maameow.overlay.clampXiangqiStatusPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiangqiStatusOverlayTest {
    @Test fun defaultStatusControlStartsOutsideEitherSupportedBoard() {
        val bottomDp = XiangqiStatusOverlay.TOP_DP + XiangqiStatusOverlay.HEIGHT_DP
        assertTrue(bottomDp * (240f / 160) < XiangqiBoardCalibration.MUMU_900X1600.top)
        assertTrue(bottomDp * (440f / 160) < XiangqiBoardCalibration.PHONE_1080X2400.top)
    }

    @Test fun draggingIsClampedToTheVisibleScreenAndKeepsValidPositions() {
        assertEquals(180 to 220, clampXiangqiStatusPosition(180, 220, 1080, 2200, 800, 198))
        assertEquals(0 to 0, clampXiangqiStatusPosition(-20, -30, 1080, 2200, 800, 198))
        assertEquals(280 to 2002, clampXiangqiStatusPosition(3000, 3000, 1080, 2200, 800, 198))
        assertEquals(0 to 0, clampXiangqiStatusPosition(50, 50, 300, 100, 800, 198))
    }
}
