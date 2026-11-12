package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.floor
import kotlin.math.round

/**
 * Pure Kotlin implementation of the pinned nano-v3 preprocessing contract.
 *
 * Keeping the pixel-array implementation separate from Bitmap/ONNX lets JVM
 * tests verify the Android tensor path without loading native Android APIs.
 */
internal object XiangqiNanoV3Preprocessor {
    private const val CROP_WIDTH = 400
    private const val CROP_HEIGHT = 450
    private const val INPUT_WIDTH = 280
    private const val INPUT_HEIGHT = 315
    private val mean = doubleArrayOf(123.675, 116.28, 103.53)
    private val standardDeviation = doubleArrayOf(58.395, 57.12, 57.375)

    fun preprocessArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
        calibration: XiangqiBoardCalibration,
        redAtTopOnScreen: Boolean,
    ): FloatArray {
        require(width > 0 && height > 0) { "Invalid screenshot dimensions" }
        require(pixels.size == width * height) { "ARGB pixel buffer size does not match screenshot" }
        calibration.validate(width, height)

        var originX = calibration.left
        var originY = calibration.top
        var scaleX = (calibration.right - calibration.left) / 350.0
        var scaleY = (calibration.bottom - calibration.top) / 400.0
        if (redAtTopOnScreen) {
            originX = calibration.right
            originY = calibration.bottom
            scaleX = -scaleX
            scaleY = -scaleY
        }

        val sourceX = IntArray(CROP_WIDTH)
        val weightX = DoubleArray(CROP_WIDTH)
        for (x in 0 until CROP_WIDTH) {
            val px = round((originX + (x - 25) * scaleX) * 32.0) / 32.0
            sourceX[x] = floor(px).toInt()
            weightX[x] = px - sourceX[x]
        }

        val crop = ByteArray(CROP_WIDTH * CROP_HEIGHT * 3)
        for (y in 0 until CROP_HEIGHT) {
            val py = round((originY + (y - 25) * scaleY) * 32.0) / 32.0
            val sourceY = floor(py).toInt()
            val weightY = py - sourceY
            for (x in 0 until CROP_WIDTH) {
                val sourceX0 = sourceX[x]
                val weight = weightX[x]
                for (channel in 0..2) {
                    val topValue =
                        pixel(pixels, width, height, sourceX0, sourceY, channel) * (1.0 - weight) +
                            pixel(pixels, width, height, sourceX0 + 1, sourceY, channel) * weight
                    val bottomValue =
                        pixel(pixels, width, height, sourceX0, sourceY + 1, channel) * (1.0 - weight) +
                            pixel(pixels, width, height, sourceX0 + 1, sourceY + 1, channel) * weight
                    val value = floor(topValue * (1.0 - weightY) + bottomValue * weightY + 0.5)
                        .toInt()
                        .coerceIn(0, 255)
                    crop[(y * CROP_WIDTH + x) * 3 + channel] = value.toByte()
                }
            }
        }

        val resizeX = DoubleArray(INPUT_WIDTH)
        val resizeWeightX = DoubleArray(INPUT_WIDTH)
        for (x in 0 until INPUT_WIDTH) {
            val px = (x + 0.5) * CROP_WIDTH / INPUT_WIDTH - 0.5
            resizeX[x] = floor(px)
            resizeWeightX[x] = px - resizeX[x]
        }

        val input = FloatArray(3 * INPUT_HEIGHT * INPUT_WIDTH)
        for (y in 0 until INPUT_HEIGHT) {
            val py = (y + 0.5) * CROP_HEIGHT / INPUT_HEIGHT - 0.5
            val sourceY = py.toInt()
            val weightY = py - sourceY
            for (x in 0 until INPUT_WIDTH) {
                val sourceX0 = resizeX[x].toInt()
                val weight = resizeWeightX[x]
                for (channel in 0..2) {
                    val topLeft = crop[(sourceY * CROP_WIDTH + sourceX0) * 3 + channel].toInt() and 0xff
                    val topRight = crop[(sourceY * CROP_WIDTH + sourceX0 + 1) * 3 + channel].toInt() and 0xff
                    val bottomLeft = crop[((sourceY + 1) * CROP_WIDTH + sourceX0) * 3 + channel].toInt() and 0xff
                    val bottomRight = crop[((sourceY + 1) * CROP_WIDTH + sourceX0 + 1) * 3 + channel].toInt() and 0xff
                    val topValue = topLeft * (1.0 - weight) + topRight * weight
                    val bottomValue = bottomLeft * (1.0 - weight) + bottomRight * weight
                    val value = floor(topValue * (1.0 - weightY) + bottomValue * weightY + 0.5)
                    input[channel * INPUT_HEIGHT * INPUT_WIDTH + y * INPUT_WIDTH + x] =
                        ((value - mean[channel]) / standardDeviation[channel]).toFloat()
                }
            }
        }
        return input
    }

    private fun pixel(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        channel: Int,
    ): Double {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0.0
        val color = pixels[y * width + x]
        return when (channel) {
            0 -> ((color ushr 16) and 0xff).toDouble()
            1 -> ((color ushr 8) and 0xff).toDouble()
            else -> (color and 0xff).toDouble()
        }
    }
}
