package ge.gogichaishvili.backgammon.engine

/** Deterministic reducer. Rejected actions return the exact original state, without mutation. */
object BackgammonEngine {
    fun newGame(): GameState = GameState(BoardState.initial(), TurnState.Opening)

    /** Resume a validated board at a turn boundary. Historical reachability is not inferred. */
    fun fromPosition(board: BoardState, player: Player): GameState {
        val result = BackgammonRules.result(board)
        return GameState(board, if (result != null) TurnState.Finished(result) else TurnState.AwaitingRoll(player))
    }

    fun legalSequences(state: GameState): List<LegalMoveSequence> =
        (state.turn as? TurnState.Moving)?.let {
            BackgammonRules.sequences(state.board, it.player, it.remainingDice)
        } ?: emptyList()

    fun legalMoves(state: GameState): List<CheckerMove> =
        frozenList(legalSequences(state).map { it.steps.first().move }.distinct())

    fun isLegalMove(state: GameState, move: CheckerMove): Boolean = move in legalMoves(state)

    fun transition(state: GameState, action: GameAction): ActionResult {
        fun reject(reason: RejectionReason) = ActionResult.Rejected(state, reason)
        val next = when (action) {
            GameAction.Reset -> newGame()
            is GameAction.OpeningRoll -> {
                if (state.turn != TurnState.Opening) return reject(RejectionReason.WRONG_PHASE)
                val dice = action.dice
                if (dice.isDouble) state else {
                    val player = if (dice.first > dice.second) Player.WHITE else Player.BLACK
                    beginRoll(state.board, player, dice)
                }
            }
            is GameAction.ApplyDice -> {
                val turn = state.turn as? TurnState.AwaitingRoll ?: return reject(RejectionReason.WRONG_PHASE)
                beginRoll(state.board, turn.player, action.dice)
            }
            is GameAction.Move -> {
                val turn = state.turn as? TurnState.Moving ?: return reject(RejectionReason.WRONG_PHASE)
                // Use the validated step's board rather than applying an unchecked caller move.
                val step = legalSequences(state).firstOrNull { it.steps.first().move == action.move }
                    ?.steps?.first() ?: return reject(RejectionReason.ILLEGAL_MOVE)
                val board = step.boardAfter
                val remaining = turn.remainingDice.toMutableList().apply { remove(action.move.die) }
                val result = BackgammonRules.result(board)
                val nextTurn = when {
                    result != null -> TurnState.Finished(result)
                    BackgammonRules.sequences(board, turn.player, remaining).isEmpty() ->
                        TurnState.Complete(turn.player, turn.roll)
                    else -> TurnState.Moving(turn.player, turn.roll, remaining)
                }
                GameState(board, nextTurn)
            }
            GameAction.EndTurn -> {
                if (state.turn is TurnState.Moving) return reject(RejectionReason.MOVES_REMAIN)
                val turn = state.turn as? TurnState.Complete ?: return reject(RejectionReason.WRONG_PHASE)
                GameState(state.board, TurnState.AwaitingRoll(turn.player.opponent))
            }
        }
        return ActionResult.Accepted(next)
    }

    private fun beginRoll(board: BoardState, player: Player, dice: DiceRoll): GameState =
        if (BackgammonRules.legalSequences(board, player, dice).isEmpty()) {
            // A fully blocked roll passes immediately; there is no manual "skip legal moves" action.
            GameState(board, TurnState.AwaitingRoll(player.opponent))
        } else GameState(board, TurnState.Moving(player, dice, dice.uses))
}
