package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.abs

/** Small visual reference for unchanged-board checks; never predicts piece identities. */
class XiangqiBoardImage private constructor(private val samples: IntArray) {
    // shortcut: thresholds cover the default wooden skin; recalibrate when adding other skins.
    fun matches(other: XiangqiBoardImage): Boolean {
        for (square in 0 until 90) {
            var changed = 0
            var masked = 0
            for (pixel in 0 until 256) {
                val a = samples[square * 256 + pixel]
                val b = other.samples[square * 256 + pixel]
                // The green hint is drawn over the game; it must not count as a manual move.
                val green = (b ushr 8) and 255
                if (green > ((b ushr 16) and 255) + 15 && green > (b and 255) + 20) {
                    masked++
                    continue
                }
                val difference = abs((a and 255) - (b and 255)) +
                    abs(((a ushr 8) and 255) - ((b ushr 8) and 255)) +
                    abs(((a ushr 16) and 255) - ((b ushr 16) and 255))
                if (difference > 60) changed++
            }
            // Ignore thin route lines and JPEG noise, but reject a changed piece/empty square.
            if (changed > 38 || masked > 96) return false
        }
        return true
    }

    companion object {
        fun sample(pixels: IntArray, width: Int, height: Int, calibration: XiangqiBoardCalibration): XiangqiBoardImage {
            require(pixels.size == width * height)
            calibration.validate(width, height)
            val dx = (calibration.right - calibration.left) / 8
            val dy = (calibration.bottom - calibration.top) / 9
            val samples = IntArray(90 * 256)
            for (row in 0 until 10) for (col in 0 until 9) {
                for (y in 0 until 16) for (x in 0 until 16) {
                    val px = (calibration.left + col * dx + (x - 7.5) * dx / 32).toInt().coerceIn(0, width - 1)
                    val py = (calibration.top + row * dy + (y - 7.5) * dy / 32).toInt().coerceIn(0, height - 1)
                    samples[(row * 9 + col) * 256 + y * 16 + x] = pixels[py * width + px]
                }
            }
            return XiangqiBoardImage(samples)
        }
    }
}
