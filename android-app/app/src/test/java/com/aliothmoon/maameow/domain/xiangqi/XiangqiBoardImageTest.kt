package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test

class XiangqiBoardImageTest {
    private val width = 500
    private val height = 560
    private val calibration = XiangqiBoardCalibration(50.0, 50.0, 450.0, 500.0, width, height)
    private fun sample(pixels: IntArray) = XiangqiBoardImage.sample(pixels, width, height, calibration)
    private fun board() = IntArray(width * height) { 0xffc89664.toInt() }

    @Test fun smallCaptureNoiseAndRecommendationDoNotEraseTheRoute() {
        val pixels = board()
        val reference = sample(pixels)
        assertTrue(reference.matches(sample(IntArray(width * height) { 0xffcb9967.toInt() })))
        for (y in 45 until 505) for (x in 245 until 255) pixels[y * width + x] = 0xff50a54a.toInt()
        assertTrue(reference.matches(sample(pixels)))
    }

    @Test fun movedPieceOrCoveredBoardInvalidatesTheReference() {
        val pixels = board()
        val reference = sample(pixels)
        for (y in 185 until 215) for (x in 185 until 215) pixels[y * width + x] = 0xff302010.toInt()
        assertFalse(reference.matches(sample(pixels)))
        assertFalse(reference.matches(sample(IntArray(width * height))))
        assertFalse(reference.matches(sample(IntArray(width * height) { 0xff00b446.toInt() })))
    }

    @Test fun wrongFrameDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { XiangqiBoardImage.sample(IntArray(12), width, height, calibration) }
    }
}
