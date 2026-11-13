package com.aliothmoon.maameow.domain.xiangqi

import android.content.Context
import com.aliothmoon.maameow.domain.models.RunMode
import kotlin.math.abs
import kotlin.math.max

internal class XiangqiCalibrationRequired(val width: Int, val height: Int) :
    IllegalArgumentException("Board calibration required for ${width}x$height")

internal fun validateManualXiangqiCalibration(calibration: XiangqiBoardCalibration) {
    calibration.validate(calibration.sourceWidth, calibration.sourceHeight)
    val dx = (calibration.right - calibration.left) / 8
    val dy = (calibration.bottom - calibration.top) / 9
    require(abs(dx - dy) / max(dx, dy) <= .12) { "Board grid spacing is inconsistent" }
}

/** Confirmed intersections are isolated by capture size and foreground/background mode. */
internal class XiangqiCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences("xiangqi_board_calibration", Context.MODE_PRIVATE)
    private fun key(mode: RunMode, width: Int, height: Int) = "${mode.name}_${width}x$height"

    fun resolve(mode: RunMode, width: Int, height: Int): XiangqiBoardCalibration {
        val saved = preferences.getString(key(mode, width, height), null)
        if (saved != null) runCatching {
            val points = saved.split(',').map(String::toDouble)
            require(points.size == 4)
            XiangqiBoardCalibration(points[0], points[1], points[2], points[3], width, height)
                .also(::validateManualXiangqiCalibration)
        }.getOrNull()?.let { return it }
        val measured = if (mode == RunMode.FOREGROUND) XiangqiBoardCalibration.PHONE_1080X2400
            else XiangqiBoardCalibration.MUMU_900X1600
        return runCatching { measured.scaledTo(width, height) }
            .getOrElse { throw XiangqiCalibrationRequired(width, height) }
    }

    fun save(mode: RunMode, calibration: XiangqiBoardCalibration) {
        validateManualXiangqiCalibration(calibration)
        preferences.edit().putString(key(mode, calibration.sourceWidth, calibration.sourceHeight),
            listOf(calibration.left, calibration.top, calibration.right, calibration.bottom).joinToString(",")).apply()
    }
}
