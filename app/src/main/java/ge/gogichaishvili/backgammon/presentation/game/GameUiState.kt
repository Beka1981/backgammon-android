package ge.gogichaishvili.backgammon.presentation.game

import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.engine.*

enum class GameStorageIssue { LOAD_FAILED, SAVE_FAILED, NEWER_SCHEMA }
enum class GameNotice { NONE, OPENING_TIE, PASSED, TURN_COMPLETE, ILLEGAL_MOVE, RESTORE_FAILED }
data class DisplayDie(val value: Int, val consumed: Boolean, val available: Boolean = !consumed)
data class CheckerTransition(
    val id: Long, val before: BoardState, val move: CheckerMove, val player: Player,
)
data class RollTransition(val id: Long)

data class GameUiState(
    val game: GameState = BackgammonEngine.newGame(),
    val ready: Boolean = false,
    val revision: Long = 0,
    val legalMoves: List<CheckerMove> = emptyList(),
    val selected: MoveSource? = null,
    val choices: List<CheckerMove> = emptyList(),
    val lastRoll: DiceRoll? = null,
    val unspentDice: List<Int> = emptyList(),
    val rolledBy: Player? = null,
    val checkerTransition: CheckerTransition? = null,
    val rollTransition: RollTransition? = null,
    val motion: MotionPolicy = MotionPolicy(),
    val notice: GameNotice = GameNotice.NONE,
    val confirmNewGame: Boolean = false,
    val resultDismissed: Boolean = false,
    val storageIssue: GameStorageIssue? = null,
) {
    val busy: Boolean get() = checkerTransition != null || rollTransition != null
    val canRoll: Boolean get() = ready && storageIssue == null && !busy && !confirmNewGame &&
        (game.turn == TurnState.Opening || game.turn is TurnState.AwaitingRoll)
    val canEndTurn: Boolean get() = ready && storageIssue == null && !busy && !confirmNewGame && game.turn is TurnState.Complete
    val selectedMoves: List<CheckerMove> get() = frozenList(legalMoves.filter { it.from == selected })
    val dice: List<DisplayDie> get() {
        val roll = lastRoll ?: return emptyList()
        val remaining = ((game.turn as? TurnState.Moving)?.remainingDice ?: unspentDice).toMutableList()
        val pool = if (game.turn == TurnState.Opening) listOf(roll.first, roll.second) else roll.uses
        return frozenList(pool.map {
            val consumed = !remaining.remove(it) && game.turn != TurnState.Opening
            DisplayDie(it, consumed, !consumed && (game.turn is TurnState.Moving || game.turn == TurnState.Opening))
        })
    }
}

sealed interface GameUiAction {
    data class Roll(val revision: Long) : GameUiAction
    data class Tap(val target: BoardTarget, val revision: Long) : GameUiAction
    data class Play(val move: CheckerMove, val revision: Long) : GameUiAction
    data class EndTurn(val revision: Long) : GameUiAction
    data class AnimationFinished(val id: Long) : GameUiAction
    data class AnimationInterrupted(val id: Long) : GameUiAction
    data class SetMotion(val policy: MotionPolicy) : GameUiAction
    data object CancelAnimations : GameUiAction
    data object RequestNewGame : GameUiAction
    data object ConfirmNewGame : GameUiAction
    data object CancelNewGame : GameUiAction
    data object DismissResult : GameUiAction
    data object CancelChoice : GameUiAction
    data object RetryPersistence : GameUiAction
}
