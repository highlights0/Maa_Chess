package com.aliothmoon.maameow.domain.xiangqi

internal data class XiangqiDisplayGeometry(val displayId: Int, val width: Int, val height: Int, val rotation: Int) {
    fun toWire() = intArrayOf(displayId, width, height, rotation)
    fun contains(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun map(calibration: XiangqiBoardCalibration) = calibration.scaledTo(width, height).also { it.validate(width, height) }

    companion object {
        fun fromWire(values: IntArray?): XiangqiDisplayGeometry? {
            if (values == null || values.size != 4 || values[0] <= 0 || values[1] !in 96..16384 ||
                values[2] !in 108..16384 || values[3] !in 0..3) return null
            return XiangqiDisplayGeometry(values[0], values[1], values[2], values[3])
        }
    }
}
