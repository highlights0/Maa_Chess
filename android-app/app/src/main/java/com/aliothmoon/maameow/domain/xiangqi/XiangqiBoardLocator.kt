package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Fits the visible 9×10 line lattice; screen size is never evidence of board position. */
internal object XiangqiBoardLocator {
    private data class Columns(val score: Double, val left: Int, val spacing: Double)
    private data class Fit(val score: Double, val calibration: XiangqiBoardCalibration)

    // shortcut: axis-aligned, near-square game boards; perspective photos need a different locator.
    fun locate(pixels: IntArray, width: Int, height: Int, preferred: XiangqiBoardCalibration? = null, trace: XiangqiTrace? = null): XiangqiBoardCalibration? {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val scale = min(1.0, 720.0 / max(width, height))
        val w = (width * scale).roundToInt()
        val h = (height * scale).roundToInt()
        if (w < 96 || h < 108) return null
        val gray = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0.0
            var count = 0
            for (sy in y * height / h until (y + 1) * height / h) {
                for (sx in x * width / w until (x + 1) * width / w) {
                    val color = pixels[sy * width + sx]
                    sum += ((color shr 16 and 255) * 299 + (color shr 8 and 255) * 587 + (color and 255) * 114) / 1000.0
                    count++
                }
            }
            gray[y * w + x] = sum / count
        }
        val vertical = DoubleArray(w * h)
        val horizontal = DoubleArray(w * h)
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            val i = y * w + x
            vertical[i] = ((min(gray[i - 2], gray[i + 2]) - gray[i] - 3) / 16).coerceIn(0.0, 1.0)
            horizontal[i] = ((min(gray[i - 2 * w], gray[i + 2 * w]) - gray[i] - 3) / 16).coerceIn(0.0, 1.0)
        }
        val v = DoubleArray(w * h)
        val horizontalPrefix = DoubleArray((w + 1) * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            v[i] = max(vertical[i], max(vertical[i - 1], vertical[i + 1]))
            horizontalPrefix[y * (w + 1) + x + 1] = horizontalPrefix[y * (w + 1) + x] +
                max(horizontal[i], max(horizontal[i - w], horizontal[i + w]))
        }
        val verticalPrefix = DoubleArray(w * (h + 1))
        for (y in 0 until h) for (x in 0 until w) {
            verticalPrefix[(y + 1) * w + x] = verticalPrefix[y * w + x] + v[y * w + x]
        }

        fun evidence(c: XiangqiBoardCalibration): Fit? {
            val left = (c.left * w / width).roundToInt()
            val right = (c.right * w / width).roundToInt()
            val top = (c.top * h / height).roundToInt()
            val bottom = (c.bottom * h / height).roundToInt()
            if (left < 2 || top < 2 || right >= w - 2 || bottom >= h - 2 || right <= left || bottom <= top) return null
            val columns = DoubleArray(9) { col ->
                val x = (left + col * (right - left) / 8.0).roundToInt()
                (verticalPrefix[(bottom + 1) * w + x] - verticalPrefix[top * w + x]) / (bottom - top + 1)
            }
            val rows = DoubleArray(8) { row ->
                val y = (top + (row + 1) * (bottom - top) / 9.0).roundToInt()
                (horizontalPrefix[y * (w + 1) + right + 1] - horizontalPrefix[y * (w + 1) + left]) / (right - left + 1)
            }
            if (columns.count { it >= .30 } < 7 || rows.count { it >= .40 } < 6 ||
                columns.first() < .40 || columns.last() < .40 ||
                columns.average() < .40 || rows.average() < .50) return null
            // The river anchors the row phase even when both back ranks are hidden by pieces.
            val riverBreaks = listOf(1, 2, 6, 7).count { col ->
                val x = (left + col * (right - left) / 8.0).roundToInt()
                fun band(row: Double): Double {
                    val y1 = (top + (row - .20) * (bottom - top) / 9).roundToInt()
                    val y2 = (top + (row + .20) * (bottom - top) / 9).roundToInt()
                    return (verticalPrefix[(y2 + 1) * w + x] - verticalPrefix[y1 * w + x]) / (y2 - y1 + 1)
                }
                val adjacent = min(band(3.5), band(5.5))
                adjacent >= .30 && band(4.5) < adjacent * .60
            }
            if (riverBreaks < 2) return null
            return Fit(columns.average() + rows.average(), c)
        }

        preferred?.takeIf { it.sourceWidth == width && it.sourceHeight == height }?.let { c ->
            evidence(c)?.let {
                trace?.event("calibration.grid-evidence", "score=${it.score} cached=true riverAnchored=true")
                return c
            }
        }
        val projection = DoubleArray(w) { x -> (0 until h).sumOf { y -> v[y * w + x] } / h }
        val proposals = mutableListOf<Columns>()
        var spacing = max(12.0, min(w, h) * .45 / 8)
        val maxSpacing = min(w * .98 / 8, h * .95 / 9)
        while (spacing <= maxSpacing) {
            val best = mutableListOf<Columns>()
            for (left in 2 until (w - 8 * spacing - 2).toInt()) {
                val score = (0..8).sumOf { col -> projection[(left + col * spacing).roundToInt()] } / 9
                best.add(Columns(score, left, spacing))
            }
            proposals.addAll(best.sortedByDescending { it.score }.take(3))
            spacing += .25
        }
        val chosen = mutableListOf<Columns>()
        for (candidate in proposals.sortedByDescending { it.score }) {
            if (chosen.any { abs(it.left - candidate.left) < candidate.spacing * .3 &&
                    abs(it.spacing - candidate.spacing) < candidate.spacing * .1 }) continue
            chosen.add(candidate)
            if (chosen.size == 12) break
        }
        val fits = mutableListOf<Fit>()
        for (candidate in chosen) {
            val right = (candidate.left + 8 * candidate.spacing).roundToInt()
            val rows = DoubleArray(h) { y ->
                (horizontalPrefix[y * (w + 1) + right + 1] - horizontalPrefix[y * (w + 1) + candidate.left]) / (right - candidate.left + 1)
            }
            var dy = candidate.spacing * .97
            while (dy <= candidate.spacing * 1.031) {
                var best: Fit? = null
                for (top in 2 until (h - 9 * dy - 2).toInt()) {
                    val score = (1..8).sumOf { row -> rows[(top + row * dy).roundToInt()] }
                    if (score < 4.0) continue
                    val fit = evidence(XiangqiBoardCalibration(
                        candidate.left.toDouble() * width / w, top.toDouble() * height / h,
                        right.toDouble() * width / w, (top + 9 * dy).roundToInt().toDouble() * height / h,
                        width, height)) ?: continue
                    if (fit.score > (best?.score ?: 0.0)) best = fit
                }
                best?.let(fits::add)
                dy += .25
            }
        }
        val best = fits.maxByOrNull { it.score } ?: run {
            trace?.event("calibration.grid-rejected", "reason=insufficient-line-or-river-evidence")
            return null
        }
        val cell = (best.calibration.right - best.calibration.left) / 8
        if (fits.any { it.score >= best.score * .95 &&
                max(abs(it.calibration.left - best.calibration.left), abs(it.calibration.top - best.calibration.top)) > cell * .35 }) {
            trace?.event("calibration.grid-rejected", "reason=ambiguous-grids bestScore=${best.score}")
            return null
        }
        trace?.event("calibration.grid-evidence", "score=${best.score} cached=false riverAnchored=true")
        return best.calibration
    }
}
