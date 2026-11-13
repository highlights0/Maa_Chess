package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.overlay.XiangqiCalibrationViewport
import org.junit.Assert.*
import org.junit.Test

class XiangqiCalibrationViewportTest {
    @Test fun letterboxed1080x2352CaptureKeepsOriginalPixelCoordinates() {
        val fit = XiangqiCalibrationViewport(1080, 2400, 1080, 2352)
        assertEquals(24f, fit.top)
        assertFalse(fit.contains(80f, 10f))
        assertEquals(XiangqiScreenPoint(80, 640), fit.imagePoint(80f, 664f))
        assertEquals(XiangqiScreenPoint(1000, 1666), fit.imagePoint(1000f, 1690f))
    }

    @Test fun enlargedBackgroundThumbnailReturnsCaptureCoordinatesAndClampsDragging() {
        val fit = XiangqiCalibrationViewport(1080, 2352, 720, 1280)
        assertEquals(XiangqiScreenPoint(66, 363), fit.imagePoint(99f, 760.5f))
        assertEquals(XiangqiScreenPoint(0, 0), fit.imagePoint(-100f, -100f))
        assertEquals(XiangqiScreenPoint(719, 1279), fit.imagePoint(2000f, 3000f))
    }
}
