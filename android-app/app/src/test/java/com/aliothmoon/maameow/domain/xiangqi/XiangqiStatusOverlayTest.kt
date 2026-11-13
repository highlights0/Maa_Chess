package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.overlay.XiangqiStatusOverlay
import org.junit.Assert.assertTrue
import org.junit.Test

class XiangqiStatusOverlayTest {
    @Test fun statusControlDoesNotCoverEitherSupportedBoard() {
        val bottomDp = XiangqiStatusOverlay.TOP_DP + XiangqiStatusOverlay.HEIGHT_DP
        assertTrue(bottomDp * (240f / 160) < XiangqiBoardCalibration.MUMU_900X1600.top)
        assertTrue(bottomDp * (440f / 160) < XiangqiBoardCalibration.PHONE_1080X2400.top)
    }
}
