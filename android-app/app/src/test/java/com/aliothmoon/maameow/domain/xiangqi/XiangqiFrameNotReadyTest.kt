package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test

class XiangqiFrameNotReadyTest {
    @Test fun onlyTransientFramesBeforeInputMayRestartAnalysisAndPlay() {
        val animation = XiangqiFrameNotReady("animation")
        assertTrue(canRescanXiangqiBeforeInput(animation, inputPending = false))
        assertFalse(canRescanXiangqiBeforeInput(animation, inputPending = true))
        assertFalse(canRescanXiangqiBeforeInput(IllegalStateException("unverified move"), inputPending = false))
        assertFalse(canRescanXiangqiBeforeInput(SecurityException("permission"), inputPending = false))
    }

    @Test fun missingGridMayWaitForNewBoardButNeverPermitResendingPendingInput() {
        val missing = XiangqiCalibrationRequired(1080, 2352)
        assertTrue(canRescanXiangqiBeforeInput(missing, inputPending = false))
        assertFalse(canRescanXiangqiBeforeInput(missing, inputPending = true))
    }
}
