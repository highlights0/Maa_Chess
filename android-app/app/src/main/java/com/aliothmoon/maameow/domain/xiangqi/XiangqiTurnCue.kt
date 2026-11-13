package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.hypot

data class XiangqiTurnCue(
    val lastMove: XiangqiMove,
    val lastSide: Char,
    val sideToMove: Char,
    val originWhiteRatio: Float,
    val destinationWhiteRatio: Float,
    val destinationByGeometry: Boolean = false,
)

/** Tiantian's white origin marker and a unique legal destination, using a halo when available. */
object XiangqiTurnCueDetector {
    // shortcut: thresholds cover the supplied default skin; validate a skin profile before supporting other themes.
    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        calibration: XiangqiBoardCalibration,
        rows: List<String>,
        redAtTop: Boolean,
        trace: XiangqiTrace? = null,
    ): XiangqiTurnCue? {
        require(pixels.size == width * height)
        calibration.validate(width, height)
        val spacing = min((calibration.right - calibration.left) / 8, (calibration.bottom - calibration.top) / 9)
        val origins = mutableListOf<Pair<XiangqiSquare, Float>>()
        val destinations = mutableListOf<Pair<XiangqiSquare, Float>>()
        val pieces = mutableListOf<Pair<XiangqiSquare, Float>>()
        for (row in 0..9) for (col in 0..8) {
            val square = XiangqiSquare(row, col)
            val point = square.screenPoint(calibration, redAtTop)
            val empty = rows[row][col] == '.'
            if (empty) {
                originMarkerRatio(pixels, width, height, point, spacing)?.let { origins += square to it }
                continue
            }
            val radius = ceil(spacing * 0.53).toInt()
            val innerSquared = spacing * spacing * 0.38 * 0.38
            val outerSquared = spacing * spacing * 0.53 * 0.53
            var count = 0
            var white = 0
            for (y in max(0, point.y - radius)..min(height - 1, point.y + radius)) {
                for (x in max(0, point.x - radius)..min(width - 1, point.x + radius)) {
                    val dx = x - point.x
                    val dy = y - point.y
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared < innerSquared || distanceSquared > outerSquared) continue
                    count++
                    if (isWhite(pixels[y * width + x])) white++
                }
            }
            val ratio = if (count == 0) 0f else white.toFloat() / count
            pieces += square to ratio
            if (white >= 8 && ratio >= 0.004f) destinations += square to ratio
        }
        fun candidates(targets: List<Pair<XiangqiSquare, Float>>, geometry: Boolean): List<XiangqiTurnCue> =
            origins.flatMap { origin -> targets.mapNotNull { destination ->
                val move = XiangqiMove(origin.first, destination.first)
                if (!plausible(rows, move)) return@mapNotNull null
                val side = if (rows[move.to.row][move.to.col].isUpperCase()) 'w' else 'b'
                XiangqiTurnCue(move, side, if (side == 'w') 'b' else 'w', origin.second, destination.second, geometry)
            } }
        val highlighted = candidates(destinations, false)
        val fallback = if (destinations.isEmpty()) candidates(pieces, true) else emptyList()
        val selected = if (highlighted.isNotEmpty()) highlighted.singleOrNull() else fallback.singleOrNull()
        trace?.event("turn.marker-candidates", "origins=${origins.size} haloDestinations=${destinations.size} legalHighlighted=${highlighted.size} legalWithoutHalo=${fallback.size} selected=${selected?.lastMove?.iccs}")
        return selected
    }

    private fun plausible(rows: List<String>, move: XiangqiMove): Boolean {
        val piece = rows[move.to.row][move.to.col]
        if (piece !in "KABNRCPkabnrcp") return false
        val side = if (piece.isUpperCase()) 'w' else 'b'
        val before = rows.map { it.toCharArray() }
        before[move.from.row][move.from.col] = piece
        // The captured piece is unknown; test both quiet and capture geometry without storing either as history.
        return listOf('.', if (side == 'w') 'p' else 'P').any { target ->
            before[move.to.row][move.to.col] = target
            XiangqiRules.isLegal(before.map { String(it) }, side, move)
        }
    }

    private fun isWhite(color: Int, minimum: Int = 205, spread: Int = 40): Boolean {
        val r = color ushr 16 and 255
        val g = color ushr 8 and 255
        val b = color and 255
        return minOf(r, g, b) >= minimum && maxOf(r, g, b) - minOf(r, g, b) <= spread
    }

    /** Look for a dot inside a white ring, allowing small grid offsets without accepting a white patch. */
    private fun originMarkerRatio(pixels: IntArray, width: Int, height: Int, point: XiangqiScreenPoint, spacing: Double): Float? {
        val radius = ceil(spacing * .45).toInt()
        val searchSquared = spacing * spacing * .45 * .45
        val left = max(0, point.x - radius); val right = min(width - 1, point.x + radius)
        val top = max(0, point.y - radius); val bottom = min(height - 1, point.y + radius)
        var white = 0; var sumX = 0L; var sumY = 0L
        for (y in top..bottom) for (x in left..right) {
            if ((x - point.x) * (x - point.x) + (y - point.y) * (y - point.y) > searchSquared) continue
            if (isWhite(pixels[y * width + x])) { white++; sumX += x; sumY += y }
        }
        val ratio = (white / (PI * spacing * spacing * .25 * .25)).toFloat()
        if (ratio !in .08f.. .65f) return null
        val cx = sumX.toDouble() / white; val cy = sumY.toDouble() / white
        if (hypot(cx - point.x, cy - point.y) > spacing * .30) return null
        var center = 0; var ring = 0; var quadrants = 0; var gap = 0; var whiteGap = 0
        for (y in top..bottom) for (x in left..right) {
            if ((x - point.x) * (x - point.x) + (y - point.y) * (y - point.y) > searchSquared) continue
            val distanceSquared = ((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (spacing * spacing)
            val color = pixels[y * width + x]
            // This skin's ring is translucent cream, while its center remains bright white.
            val ringPixel = isWhite(color, minimum = 190, spread = 60)
            if (distanceSquared in .0121.. .0196) { gap++; if (ringPixel) whiteGap++ }
            if (isWhite(color) && distanceSquared <= .01) center++
            if (ringPixel && distanceSquared in .0169.. .0676) {
                ring++
                val quadrant = (if (x >= cx) 1 else 0) + (if (y >= cy) 2 else 0)
                quadrants = quadrants or (1 shl quadrant)
            }
        }
        return ratio.takeIf { center >= 3 && ring >= 8 && quadrants == 15 && gap > 0 && whiteGap * 2 < gap }
    }
}
