package com.aliothmoon.maameow.domain.xiangqi

import kotlin.math.abs

/** Board movement and king-safety checks; repetition adjudication stays with Pikafish history. */
object XiangqiRules {
    const val START_BOARD = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR"

    fun expand(board: String): List<String> {
        val rows = board.split('/').map { rank ->
            buildString {
                for (symbol in rank) {
                    when (symbol) {
                        in '1'..'9' -> repeat(symbol.digitToInt()) { append('.') }
                        in "KABNRCPkabnrcp" -> append(symbol)
                        else -> error("Invalid board symbol")
                    }
                }
            }.also { require(it.length == 9) }
        }
        require(rows.size == 10)
        return rows
    }

    fun board(rows: List<String>): String {
        require(rows.size == 10 && rows.all { it.length == 9 && it.all { c -> c in ".KABNRCPkabnrcp" } })
        return rows.joinToString("/") { row ->
            buildString {
                var empty = 0
                for (piece in row) {
                    if (piece == '.') empty++ else {
                        if (empty > 0) append(empty)
                        empty = 0
                        append(piece)
                    }
                }
                if (empty > 0) append(empty)
            }
        }
    }

    fun apply(rows: List<String>, move: XiangqiMove): List<String> = rows.map { it.toCharArray() }.also {
        it[move.to.row][move.to.col] = it[move.from.row][move.from.col]
        it[move.from.row][move.from.col] = '.'
    }.map { String(it) }

    fun legalMoves(rows: List<String>, side: Char): List<XiangqiMove> = buildList {
        require(side in "wb")
        for (r in 0..9) for (c in 0..8) {
            if (rows[r][c] == '.' || rows[r][c].isUpperCase() != (side == 'w')) continue
            for (tr in 0..9) for (tc in 0..8) {
                val move = XiangqiMove(XiangqiSquare(r, c), XiangqiSquare(tr, tc))
                if (isLegal(rows, side, move)) add(move)
            }
        }
    }

    fun isLegal(rows: List<String>, side: Char, move: XiangqiMove): Boolean {
        if (side !in "wb" || !inside(move.from) || !inside(move.to)) return false
        val piece = rows[move.from.row][move.from.col]
        val target = rows[move.to.row][move.to.col]
        if (piece !in "KABNRCPkabnrcp" || piece.isUpperCase() != (side == 'w') ||
            target in "Kk" || (target != '.' && target.isUpperCase() == piece.isUpperCase())) return false
        return pseudoLegal(rows, move) && !inCheck(apply(rows, move), side)
    }

    fun inCheck(rows: List<String>, side: Char): Boolean {
        val king = if (side == 'w') 'K' else 'k'
        val r = rows.indexOfFirst { king in it }
        if (r < 0) return true
        val square = XiangqiSquare(r, rows[r].indexOf(king))
        return (0..9).any { row -> (0..8).any { col ->
            val piece = rows[row][col]
            piece != '.' && piece.isUpperCase() != (side == 'w') &&
                pseudoLegal(rows, XiangqiMove(XiangqiSquare(row, col), square))
        } }
    }

    fun transition(before: List<String>, after: List<String>, side: Char): XiangqiMove? {
        val changed = (0..9).flatMap { r -> (0..8).map { c -> XiangqiSquare(r, c) } }
            .filter { before[it.row][it.col] != after[it.row][it.col] }
        if (changed.size != 2) return null
        val from = changed.singleOrNull { after[it.row][it.col] == '.' } ?: return null
        val to = changed.single { it != from }
        val move = XiangqiMove(from, to)
        return move.takeIf { isLegal(before, side, it) && apply(before, it) == after }
    }

    private fun inside(s: XiangqiSquare) = s.row in 0..9 && s.col in 0..8

    private fun pseudoLegal(rows: List<String>, move: XiangqiMove): Boolean {
        val (from, to) = move
        val dr = to.row - from.row
        val dc = to.col - from.col
        if (dr == 0 && dc == 0) return false
        val piece = rows[from.row][from.col]
        val red = piece.isUpperCase()
        fun palace() = to.col in 3..5 && to.row in (if (red) 7..9 else 0..2)
        fun blockers(): Int {
            if (dr != 0 && dc != 0) return -1
            val steps = maxOf(abs(dr), abs(dc))
            return (1 until steps).count {
                rows[from.row + dr.sign() * it][from.col + dc.sign() * it] != '.'
            }
        }
        return when (piece.uppercaseChar()) {
            'K' -> (abs(dr) + abs(dc) == 1 && palace()) ||
                (dc == 0 && rows[to.row][to.col].uppercaseChar() == 'K' && blockers() == 0)
            'A' -> abs(dr) == 1 && abs(dc) == 1 && palace()
            'B' -> abs(dr) == 2 && abs(dc) == 2 && (if (red) to.row >= 5 else to.row <= 4) &&
                rows[from.row + dr / 2][from.col + dc / 2] == '.'
            'N' -> when {
                abs(dr) == 2 && abs(dc) == 1 -> rows[from.row + dr / 2][from.col] == '.'
                abs(dr) == 1 && abs(dc) == 2 -> rows[from.row][from.col + dc / 2] == '.'
                else -> false
            }
            'R' -> blockers() == 0
            'C' -> blockers() == if (rows[to.row][to.col] == '.') 0 else 1
            'P' -> (dc == 0 && dr == if (red) -1 else 1) ||
                (dr == 0 && abs(dc) == 1 && (if (red) from.row <= 4 else from.row >= 5))
            else -> false
        }
    }

    private fun Int.sign() = compareTo(0)
}
