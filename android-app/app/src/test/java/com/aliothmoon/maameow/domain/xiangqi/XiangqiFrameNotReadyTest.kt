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
}
