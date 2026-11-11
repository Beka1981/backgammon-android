package ge.gogichaishvili.backgammon.domain.match

import ge.gogichaishvili.backgammon.engine.*

/** Committed game only. Animations, pending input and random generator state never enter storage. */
data class MatchRecord(
    val game: GameState,
    val roll: DiceRoll? = null,
    val remainingDice: List<Int> = emptyList(),
    val rolledBy: Player? = null,
    val resultDismissed: Boolean = false,
) {
    fun validated(): MatchRecord {
        val pool = roll?.uses?.toMutableList() ?: mutableListOf()
        require(remainingDice.size <= 4 && remainingDice.all { pool.remove(it) })
        when (val turn = game.turn) {
            TurnState.Opening -> {
                require(roll == null || roll.isDouble)
                require(rolledBy == null)
            }
            is TurnState.Moving -> {
                require(roll == turn.roll && remainingDice == turn.remainingDice && rolledBy == turn.player)
                require(BackgammonEngine.legalMoves(game).isNotEmpty())
            }
            is TurnState.Complete -> {
                require(roll == turn.roll && rolledBy == turn.player)
                if (remainingDice.isNotEmpty()) {
                    val probe = GameState(game.board, TurnState.Moving(turn.player, turn.roll, remainingDice))
                    require(BackgammonEngine.legalMoves(probe).isEmpty())
                }
            }
            is TurnState.AwaitingRoll -> {
                if (roll == null) require(remainingDice.isEmpty() && rolledBy == null)
                else {
                    require(rolledBy == turn.player.opponent && remainingDice == roll.uses)
                    val beforePass = BackgammonEngine.fromPosition(game.board, requireNotNull(rolledBy))
                    val outcome = BackgammonEngine.transition(beforePass, GameAction.ApplyDice(roll))
                    require(outcome is ActionResult.Accepted && outcome.state == game)
                }
            }
            is TurnState.Finished -> require(roll != null && rolledBy == turn.result.winner)
        }
        require(!resultDismissed || game.result != null)
        return copy(remainingDice = frozenList(remainingDice))
    }
}

data class MatchLoad(
    val record: MatchRecord? = null,
    val recoveredCorruption: Boolean = false,
    val unsupportedVersion: Int? = null,
)

interface MatchRepository {
    suspend fun load(): MatchLoad
    /** Returns only after the atomic durable write succeeds. Errors must not acknowledge input. */
    suspend fun save(record: MatchRecord)
}
