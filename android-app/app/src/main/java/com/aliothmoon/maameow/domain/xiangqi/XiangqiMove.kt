package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.roundToInt

data class XiangqiScreenPoint(val x: Int, val y: Int)

fun XiangqiSquare.screenPoint(
    calibration: XiangqiBoardCalibration,
    redAtTopOnScreen: Boolean,
): XiangqiScreenPoint {
    val displayCol = if (redAtTopOnScreen) 8 - col else col
    val displayRow = if (redAtTopOnScreen) 9 - row else row
    return XiangqiScreenPoint(
        (calibration.left + displayCol * (calibration.right - calibration.left) / 8).roundToInt(),
        (calibration.top + displayRow * (calibration.bottom - calibration.top) / 9).roundToInt(),
    )
}

data class XiangqiMove(val from: XiangqiSquare, val to: XiangqiSquare) {
    val iccs: String get() = "${'a' + from.col}${9 - from.row}${'a' + to.col}${9 - to.row}"
    fun isObservedAfter(before: List<String>, after: List<String>): Boolean =
        before.size == 10 && after.size == 10 &&
            before.all { it.length == 9 } && after.all { it.length == 9 } &&
            XiangqiRules.apply(before, this) == after

    companion object {
        fun parse(iccs: String, rows: List<String>, side: Char): XiangqiMove? {
            if (iccs.length != 4 || rows.size != 10 || rows.any { it.length != 9 }) return null
            val from = square(iccs[0], iccs[1]) ?: return null
            val to = square(iccs[2], iccs[3]) ?: return null
            if (from == to) return null
            val piece = rows[from.row][from.col]
            val target = rows[to.row][to.col]
            if (piece !in "KABNRCPkabnrcp" ||
                (side == 'w' && !piece.isUpperCase()) ||
                (side == 'b' && !piece.isLowerCase()) ||
                side !in "wb" ||
                (target != '.' && (target == 'x' || piece.isUpperCase() == target.isUpperCase()))
            ) return null
            return XiangqiMove(from, to)
        }

        private fun square(file: Char, rank: Char): XiangqiSquare? {
            if (file !in 'a'..'i' || rank !in '0'..'9') return null
            return XiangqiSquare(9 - (rank - '0'), file - 'a')
        }
    }
}

fun xiangqiPieceName(symbol: Char): String = when (symbol) {
    'K' -> "帥"
    'A' -> "仕"
    'B' -> "相"
    'N' -> "馬"
    'R' -> "車"
    'C' -> "炮"
    'P' -> "兵"
    'k' -> "將"
    'a' -> "士"
    'b' -> "象"
    'n' -> "馬"
    'r' -> "車"
    'c' -> "炮"
    'p' -> "卒"
    else -> "?"
}
