package ge.gogichaishvili.backgammon.presentation.game

import ge.gogichaishvili.backgammon.engine.*

/** Versioned, bounded SavedStateHandle payload, not a long-term storage/network format. */
internal object GameSnapshot {
    const val KEY = "local_game_v1"
    fun encode(s: GameUiState): IntArray {
        val turn = s.game.turn
        val phase = when (turn) {
            TurnState.Opening -> 0
            is TurnState.AwaitingRoll -> 1
            is TurnState.Moving -> 2
            is TurnState.Complete -> 3
            is TurnState.Finished -> 4
        }
        val remaining = (turn as? TurnState.Moving)?.remainingDice ?: s.unspentDice
        val roll = when (turn) {
            is TurnState.Moving -> turn.roll
            is TurnState.Complete -> turn.roll
            else -> s.lastRoll
        }
        return (listOf(1) + s.game.board.points.map {
            if (it.owner == Player.BLACK) -it.count else it.count
        } + listOf(s.game.board.bar.white, s.game.board.bar.black,
            s.game.board.borneOff.white, s.game.board.borneOff.black,
            phase, s.game.currentPlayer?.ordinal ?: -1, roll?.first ?: 0, roll?.second ?: 0,
            remaining.size) + remaining + listOf(
            when (val selected = s.selected) { null -> -1; MoveSource.Bar -> 0; is MoveSource.OnPoint -> selected.number },
            s.rolledBy?.ordinal ?: -1, if (s.resultDismissed) 1 else 0, if (s.confirmNewGame) 1 else 0)
        ).toIntArray()
    }

    fun decode(raw: IntArray): GameUiState? = runCatching {
        require(raw.size in 38..42)
        var index = 0
        fun next(): Int = raw[index++]
        require(next() == 1)
        val points = List(24) {
            val value = next()
            require(value in -15..15)
            when { value < 0 -> Point(Player.BLACK, -value); value > 0 -> Point(Player.WHITE, value); else -> Point() }
        }
        val board = BoardState(points, CheckerCounts(next(), next()), CheckerCounts(next(), next()))
        val phase = next()
        val playerCode = next()
        val player = if (playerCode == -1) null else Player.entries[playerCode]
        val first = next()
        val second = next()
        val roll = if (first == 0 && second == 0) null else DiceRoll(first, second)
        val count = next()
        require(count in 0..4)
        val remaining = List(count) { next() }
        val pool = roll?.uses?.toMutableList() ?: mutableListOf()
        require(remaining.all { pool.remove(it) })
        val turn = when (phase) {
            0 -> TurnState.Opening
            1 -> TurnState.AwaitingRoll(requireNotNull(player))
            2 -> TurnState.Moving(requireNotNull(player), requireNotNull(roll), remaining)
            3 -> TurnState.Complete(requireNotNull(player), requireNotNull(roll))
            4 -> TurnState.Finished(requireNotNull(BackgammonRules.result(board)))
            else -> error("Unknown phase")
        }
        val game = GameState(board, turn)
        val legal = BackgammonEngine.legalMoves(game)
        require(turn !is TurnState.Moving || legal.isNotEmpty())
        val selectedCode = next()
        val selected = when (selectedCode) { -1 -> null; 0 -> MoveSource.Bar; else -> MoveSource.OnPoint(selectedCode) }
        val ownerCode = next()
        val owner = if (ownerCode == -1) null else Player.entries[ownerCode]
        val dismissed = next().also { require(it in 0..1) } == 1
        val confirmation = next().also { require(it in 0..1) } == 1
        require(index == raw.size)
        GameUiState(game = game, ready = true, revision = 1, legalMoves = legal,
            selected = selected?.takeIf { source -> legal.any { it.from == source } },
            lastRoll = roll, unspentDice = frozenList(remaining), rolledBy = owner, resultDismissed = dismissed, confirmNewGame = confirmation)
    }.getOrNull()
}
