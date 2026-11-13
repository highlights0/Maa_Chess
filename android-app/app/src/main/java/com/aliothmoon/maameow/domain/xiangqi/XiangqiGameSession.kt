package com.aliothmoon.maameow.domain.xiangqi

/** Observed moves from a known start or an explicitly marked search baseline. */
data class XiangqiGameSession(
    val initialFen: String,
    val moves: List<String>,
    val rows: List<String>,
    val side: Char,
    val historyKnown: Boolean = true,
) {
    val positionCommand: String
        get() = "position fen $initialFen" + if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}"

    fun observe(observed: List<String>): XiangqiGameSession {
        if (observed == rows) return this
        val move = XiangqiRules.transition(rows, observed, side)
        val opponent = if (side == 'w') 'b' else 'w'
        if (move != null) return copy(moves = moves + move.iccs, rows = observed, side = opponent)
        // A manual move and the opponent's reply can both finish between captures.
        val pairs = XiangqiRules.legalMoves(rows, side).mapNotNull { first ->
            XiangqiRules.transition(XiangqiRules.apply(rows, first), observed, opponent)
                ?.let { listOf(first.iccs, it.iccs) }
        }
        val pair = pairs.singleOrNull()
            ?: error("Observed board has no unique legal one-or-two-ply continuation")
        return copy(moves = moves + pair, rows = observed)
    }

    companion object {
        /** Search-only baseline. Zero counters here are not an observed rule60 or game number. */
        fun snapshot(board: String, side: Char): XiangqiGameSession =
            fresh(board, side).copy(historyKnown = false)

        fun reconnect(previous: XiangqiGameSession?, rows: List<String>, side: Char?, newOpening: Boolean = false): XiangqiGameSession? {
            if (side == null) return opening(rows)
            if (newOpening) opening(rows)?.takeIf { side == 'w' }?.let { return it }
            if (previous != null) {
                runCatching { previous.observe(rows) }.getOrNull()?.takeIf { it.side == side }?.let { return it }
            }
            opening(rows)?.takeIf { side == 'w' }?.let { return it }
            return snapshot(XiangqiRules.board(rows), side)
        }

        fun fresh(board: String, side: Char): XiangqiGameSession =
            fromFen(XiangqiEnginePosition.freshFen(board, side))

        fun fromFen(fen: String, history: List<String> = emptyList()): XiangqiGameSession {
            val fields = fen.trim().split(Regex("\\s+"))
            require(fields.size == 6 && fields[1] in listOf("w", "b") && fields[2] == "-" && fields[3] == "-")
            require(fields[4].toIntOrNull()?.let { it >= 0 } == true)
            require(fields[5].toIntOrNull()?.let { it >= 1 } == true)
            XiangqiEnginePosition.freshFen(fields[0], fields[1].single())
            var session = XiangqiGameSession(fields.joinToString(" "), emptyList(), XiangqiRules.expand(fields[0]), fields[1].single())
            for (iccs in history) {
                val move = XiangqiMove.parse(iccs, session.rows, session.side)
                    ?: error("Invalid history move")
                require(XiangqiRules.isLegal(session.rows, session.side, move))
                session = session.observe(XiangqiRules.apply(session.rows, move))
            }
            return session
        }

        fun opening(rows: List<String>): XiangqiGameSession? =
            if (XiangqiRules.board(rows) == XiangqiRules.START_BOARD) fresh(XiangqiRules.START_BOARD, 'w') else null
    }
}
