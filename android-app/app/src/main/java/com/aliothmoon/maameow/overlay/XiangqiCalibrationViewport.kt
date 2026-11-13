package com.aliothmoon.maameow.overlay

import com.aliothmoon.maameow.domain.xiangqi.XiangqiScreenPoint
import kotlin.math.min
import kotlin.math.roundToInt

internal class XiangqiCalibrationViewport(viewWidth: Int, viewHeight: Int, private val imageWidth: Int, private val imageHeight: Int) {
    init { require(viewWidth > 0 && viewHeight > 0 && imageWidth > 0 && imageHeight > 0) }
    val scale = min(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    val left = (viewWidth - imageWidth * scale) / 2
    val top = (viewHeight - imageHeight * scale) / 2
    val right = left + imageWidth * scale
    val bottom = top + imageHeight * scale

    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    fun imagePoint(x: Float, y: Float) = XiangqiScreenPoint(
        ((x - left) / scale).roundToInt().coerceIn(0, imageWidth - 1),
        ((y - top) / scale).roundToInt().coerceIn(0, imageHeight - 1),
    )
}
