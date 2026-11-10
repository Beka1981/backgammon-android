package ge.gogichaishvili.backgammon.engine

/** Stateless standard short-backgammon rules, with no Android or random-number dependencies. */
object BackgammonRules {
    fun canBearOff(board: BoardState, player: Player): Boolean =
        board.bar[player] == 0 && (1..24).none { it !in player.home && board.point(it).owner == player }

    fun result(board: BoardState): GameResult? {
        val winner = Player.entries.firstOrNull { board.borneOff[it] == 15 } ?: return null
        val loser = winner.opponent
        val kind = when {
            board.borneOff[loser] > 0 -> WinKind.SINGLE
            board.bar[loser] > 0 || winner.home.any { board.point(it).owner == loser } -> WinKind.BACKGAMMON
            else -> WinKind.GAMMON
        }
        return GameResult(winner, kind)
    }

    fun legalSequences(board: BoardState, player: Player, roll: DiceRoll): List<LegalMoveSequence> =
        sequences(board, player, roll.uses)

    /**
     * Enumerate both dice orders (four identical uses for doubles), then filter globally.
     * Checking only individual moves is insufficient: an otherwise legal move can prevent
     * playing the second die. Depth is bounded by four; no cross-game cache or mutable state.
     */
    internal fun sequences(board: BoardState, player: Player, dice: List<Int>): List<LegalMoveSequence> {
        if (result(board) != null) return emptyList()
        val candidates = tails(board, player, dice)
        val maximum = candidates.maxOfOrNull { it.size } ?: 0
        if (maximum == 0) return emptyList()
        var best = candidates.filter { it.size == maximum }
        if (maximum == 1 && dice.distinct().size == 2) {
            val higherPlayable = best.maxOf { it.single().move.die }
            best = best.filter { it.single().move.die == higherPlayable }
        }
        return frozenList(best.map { LegalMoveSequence(board, it) })
    }

    private fun tails(board: BoardState, player: Player, dice: List<Int>): List<List<MoveStep>> {
        if (dice.isEmpty() || result(board) != null) return listOf(emptyList())
        val plays = dice.distinct().flatMap { die ->
            singleMoves(board, player, die).flatMap { move ->
                val next = applySingle(board, player, move)
                val remaining = dice.toMutableList().apply { remove(die) }
                tails(next, player, remaining).map { listOf(MoveStep(move, next)) + it }
            }
        }
        return plays.ifEmpty { listOf(emptyList()) }
    }

    private fun singleMoves(board: BoardState, player: Player, die: Int): List<CheckerMove> {
        if (board.bar[player] > 0) {
            val number = player.entryPoint(die)
            return if (isOpen(board.point(number), player)) listOf(
                CheckerMove(MoveSource.Bar, MoveDestination.OnPoint(number), die)) else emptyList()
        }
        val bearingOff = canBearOff(board, player)
        return (1..24).filter { board.point(it).owner == player }.mapNotNull { source ->
            val destination = source + player.direction * die
            when {
                destination in 1..24 && isOpen(board.point(destination), player) ->
                    CheckerMove(MoveSource.OnPoint(source), MoveDestination.OnPoint(destination), die)
                destination !in 1..24 && bearingOff && canRemove(board, player, source, die) ->
                    CheckerMove(MoveSource.OnPoint(source), MoveDestination.Off, die)
                else -> null
            }
        }
    }

    private fun isOpen(point: Point, player: Player): Boolean = point.owner != player.opponent || point.count == 1

    private fun canRemove(board: BoardState, player: Player, source: Int, die: Int): Boolean {
        val distance = player.distanceToOff(source)
        return die == distance || (die > distance && (1..24).none {
            board.point(it).owner == player && player.distanceToOff(it) > distance
        })
    }

    /** Only called for enumerated legal single moves. All public applications validate whole-turn legality. */
    private fun applySingle(board: BoardState, player: Player, move: CheckerMove): BoardState {
        val points = board.points.toMutableList()
        var bar = board.bar
        var off = board.borneOff
        when (val source = move.from) {
            MoveSource.Bar -> bar = bar.add(player, -1)
            is MoveSource.OnPoint -> {
                val count = board.point(source.number).count - 1
                points[source.number - 1] = if (count == 0) Point() else Point(player, count)
            }
        }
        when (val destination = move.to) {
            MoveDestination.Off -> off = off.add(player, 1)
            is MoveDestination.OnPoint -> {
                val current = points[destination.number - 1]
                val count = if (current.owner == player.opponent) {
                    bar = bar.add(player.opponent, 1)
                    1
                } else current.count + 1
                points[destination.number - 1] = Point(player, count)
            }
        }
        return BoardState(points, bar, off)
    }
}
