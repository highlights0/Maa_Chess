package com.aliothmoon.maameow.domain.xiangqi

import android.content.Context
import com.aliothmoon.maameow.domain.models.RunMode
import kotlin.math.abs
import kotlin.math.max

internal class XiangqiCalibrationRequired(val width: Int, val height: Int) :
    XiangqiFrameNotReady("No reliable board grid for ${width}x$height")

internal fun validateManualXiangqiCalibration(calibration: XiangqiBoardCalibration) {
    calibration.validate(calibration.sourceWidth, calibration.sourceHeight)
    val dx = (calibration.right - calibration.left) / 8
    val dy = (calibration.bottom - calibration.top) / 9
    require(abs(dx - dy) / max(dx, dy) <= .12) { "Board grid spacing is inconsistent" }
}

/** Detected intersections are checked against every frame; manual points are only a fallback. */
internal class XiangqiCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences("xiangqi_board_calibration", Context.MODE_PRIVATE)
    private val detected = mutableMapOf<String, XiangqiBoardCalibration>()
    private fun key(mode: RunMode, width: Int, height: Int) = "${mode.name}_${width}x$height"

    private fun saved(mode: RunMode, width: Int, height: Int): XiangqiBoardCalibration? {
        val saved = preferences.getString(key(mode, width, height), null)
        if (saved != null) runCatching {
            val points = saved.split(',').map(String::toDouble)
            require(points.size == 4)
            XiangqiBoardCalibration(points[0], points[1], points[2], points[3], width, height)
                .also(::validateManualXiangqiCalibration)
        }.getOrNull()?.let { return it }
        return null
    }

    @Synchronized
    fun resolve(mode: RunMode, width: Int, height: Int, pixels: IntArray, trace: XiangqiTrace? = null): XiangqiBoardCalibration {
        val key = key(mode, width, height)
        val manual = saved(mode, width, height)
        val automatic = XiangqiBoardLocator.locate(pixels, width, height, detected[key] ?: manual, trace)
        val result = automatic ?: manual ?: run {
            trace?.event("calibration.unresolved", "mode=$mode frame=${width}x$height reason=no-unambiguous-grid-or-manual-points")
            throw XiangqiCalibrationRequired(width, height)
        }
        if (automatic != null) detected[key] = automatic else detected.remove(key)
        trace?.event("calibration.resolved", "mode=$mode source=${if (automatic != null) "visible-grid" else "manual"} calibration=$result")
        return result
    }

    @Synchronized
    fun save(mode: RunMode, calibration: XiangqiBoardCalibration) {
        validateManualXiangqiCalibration(calibration)
        preferences.edit().putString(key(mode, calibration.sourceWidth, calibration.sourceHeight),
            listOf(calibration.left, calibration.top, calibration.right, calibration.bottom).joinToString(",")).apply()
        detected.remove(key(mode, calibration.sourceWidth, calibration.sourceHeight))
    }
}
