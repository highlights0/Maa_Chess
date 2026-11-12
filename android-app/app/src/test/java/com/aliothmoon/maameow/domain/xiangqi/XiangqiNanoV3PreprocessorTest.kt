package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiangqiNanoV3PreprocessorTest {
    @Test
    fun solidRgbFrameProducesExpectedNchwTensorAndNormalization() {
        val width = 900
        val height = 1600
        val solidArgb = 0xff0c2238.toInt()
        val pixels = IntArray(width * height) { solidArgb }

        val tensor = XiangqiNanoV3Preprocessor.preprocessArgb(
            pixels = pixels,
            width = width,
            height = height,
            calibration = XiangqiBoardCalibration.MUMU_900X1600,
            redAtTopOnScreen = false,
        )

        val planeSize = 315 * 280
        assertEquals(3 * planeSize, tensor.size)
        val expected = floatArrayOf(
            ((12.0 - 123.675) / 58.395).toFloat(),
            ((34.0 - 116.28) / 57.12).toFloat(),
            ((56.0 - 103.53) / 57.375).toFloat(),
        )
        for (channel in 0..2) {
            for (index in 0 until planeSize) {
                assertEquals(expected[channel], tensor[channel * planeSize + index], 1e-6f)
            }
        }
    }

    @Test
    fun rotatingScreenOrientationChangesTheModelInput() {
        val width = 900
        val height = 1600
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val red = x and 0xff
            val green = y and 0xff
            val blue = (x + y) and 0xff
            (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }

        val normal = XiangqiNanoV3Preprocessor.preprocessArgb(
            pixels,
            width,
            height,
            XiangqiBoardCalibration.MUMU_900X1600,
            redAtTopOnScreen = false,
        )
        val rotated = XiangqiNanoV3Preprocessor.preprocessArgb(
            pixels,
            width,
            height,
            XiangqiBoardCalibration.MUMU_900X1600,
            redAtTopOnScreen = true,
        )

        assertEquals(3 * 315 * 280, normal.size)
        assertFalse(normal.contentEquals(rotated))
        assertTrue(normal.all(Float::isFinite))
        assertTrue(rotated.all(Float::isFinite))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPixelBufferWithIncorrectSize() {
        XiangqiNanoV3Preprocessor.preprocessArgb(
            pixels = intArrayOf(0),
            width = 900,
            height = 1600,
            calibration = XiangqiBoardCalibration.MUMU_900X1600,
            redAtTopOnScreen = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCalibrationOutsideScreenshot() {
        XiangqiNanoV3Preprocessor.preprocessArgb(
            pixels = IntArray(100 * 100),
            width = 100,
            height = 100,
            calibration = XiangqiBoardCalibration.MUMU_900X1600,
            redAtTopOnScreen = false,
        )
    }
}
