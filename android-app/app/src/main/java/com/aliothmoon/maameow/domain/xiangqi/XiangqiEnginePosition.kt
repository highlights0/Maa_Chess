package com.aliothmoon.maameow.domain.xiangqi

/** A fresh puzzle has known counters; an arbitrary screenshot does not. */
internal object XiangqiEnginePosition {
    private const val PIECES = "KABNRCPkabnrcp"

    fun freshFen(board: String, side: Char): String {
        require(side == 'w' || side == 'b')
        val rows = board.split('/')
        require(rows.size == 10)
        val squares = rows.map { rank ->
            buildString {
                for (symbol in rank) {
                    when {
                        symbol in '1'..'9' -> repeat(symbol.digitToInt()) { append('.') }
                        symbol in PIECES -> append(symbol)
                        else -> throw IllegalArgumentException("Invalid board symbol")
                    }
                }
            }.also { require(it.length == 9) }
        }
        require(squares.sumOf { row -> row.count { it == 'K' } } == 1)
        require(squares.sumOf { row -> row.count { it == 'k' } } == 1)
        val redRow = squares.indexOfFirst { 'K' in it }
        val blackRow = squares.indexOfFirst { 'k' in it }
        val redCol = squares[redRow].indexOf('K')
        val blackCol = squares[blackRow].indexOf('k')
        require(redRow in 7..9 && redCol in 3..5)
        require(blackRow in 0..2 && blackCol in 3..5)
        if (redCol == blackCol) {
            require((blackRow + 1 until redRow).any { squares[it][redCol] != '.' })
        }
        return "$board $side - - 0 1"
    }

    fun requireMoveOnBoard(board: String, side: Char, move: String) {
        require(Regex("[a-i][0-9][a-i][0-9]").matches(move))
        val rows = board.split('/').map { rank ->
            buildString {
                rank.forEach { symbol ->
                    if (symbol in '1'..'9') repeat(symbol.digitToInt()) { append('.') }
                    else append(symbol)
                }
            }
        }
        val from = rows[9 - move[1].digitToInt()][move[0] - 'a']
        val to = rows[9 - move[3].digitToInt()][move[2] - 'a']
        require(from in PIECES && from.isUpperCase() == (side == 'w'))
        require(to == '.' || to.isUpperCase() != from.isUpperCase())
    }
}
