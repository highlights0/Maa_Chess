package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class XiangqiTurnCue(
    val lastMove: XiangqiMove,
    val lastSide: Char,
    val sideToMove: Char,
    val originWhiteRatio: Float,
    val destinationWhiteRatio: Float,
)

/** Tiantian's empty-square white marker plus the destination piece's white halo. */
object XiangqiTurnCueDetector {
    // shortcut: thresholds cover the supplied default skin; validate a skin profile before supporting other themes.
    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        calibration: XiangqiBoardCalibration,
        rows: List<String>,
        redAtTop: Boolean,
    ): XiangqiTurnCue? {
        require(pixels.size == width * height)
        calibration.validate(width, height)
        val spacing = min((calibration.right - calibration.left) / 8, (calibration.bottom - calibration.top) / 9)
        val origins = mutableListOf<Pair<XiangqiSquare, Float>>()
        val destinations = mutableListOf<Pair<XiangqiSquare, Float>>()
        val radius = ceil(spacing * 0.53).toInt()
        for (row in 0..9) for (col in 0..8) {
            val square = XiangqiSquare(row, col)
            val point = square.screenPoint(calibration, redAtTop)
            val empty = rows[row][col] == '.'
            var count = 0
            var white = 0
            for (y in max(0, point.y - radius)..min(height - 1, point.y + radius)) {
                for (x in max(0, point.x - radius)..min(width - 1, point.x + radius)) {
                    val dx = (x - point.x) / spacing
                    val dy = (y - point.y) / spacing
                    val distanceSquared = dx * dx + dy * dy
                    if (if (empty) distanceSquared > 0.25 * 0.25
                        else distanceSquared < 0.38 * 0.38 || distanceSquared > 0.53 * 0.53) continue
                    count++
                    val color = pixels[y * width + x]
                    val r = color ushr 16 and 255
                    val g = color ushr 8 and 255
                    val b = color and 255
                    if (minOf(r, g, b) >= 205 && maxOf(r, g, b) - minOf(r, g, b) <= 40) white++
                }
            }
            val ratio = if (count == 0) 0f else white.toFloat() / count
            if (empty && ratio >= 0.08f) origins += square to ratio
            if (!empty && white >= 8 && ratio >= 0.004f) destinations += square to ratio
        }
        val origin = origins.singleOrNull() ?: return null
        val destination = destinations.singleOrNull() ?: return null
        val move = XiangqiMove(origin.first, destination.first)
        val piece = rows[move.to.row][move.to.col]
        if (piece !in "KABNRCPkabnrcp") return null
        val side = if (piece.isUpperCase()) 'w' else 'b'
        val before = rows.map { it.toCharArray() }
        before[move.from.row][move.from.col] = piece
        // The captured piece is unknown; test both quiet and capture geometry without storing either as history.
        val plausible = listOf('.', if (side == 'w') 'p' else 'P').any { target ->
            before[move.to.row][move.to.col] = target
            XiangqiRules.isLegal(before.map { String(it) }, side, move)
        }
        if (!plausible) return null
        return XiangqiTurnCue(move, side, if (side == 'w') 'b' else 'w', origin.second, destination.second)
    }
}
