package ge.gogichaishvili.backgammon.engine

import org.junit.Assert.*
import org.junit.Test

class GameTransitionsTest {
    @Test fun newGameWaitsForAnOpeningRollWithoutChoosingAPlayer() {
        val state = BackgammonEngine.newGame()
        assertEquals(BoardState.initial(), state.board)
        assertEquals(TurnState.Opening, state.turn)
        assertNull(state.currentPlayer)
        assertNull(state.result)
        assertTrue(BackgammonEngine.legalSequences(state).isEmpty())
    }

    @Test fun allOpeningDiceDetermineCorrectPlayerAndUseBothOriginalDice() {
        (1..6).forEach { white ->
            (1..6).forEach { black ->
                val initial = BackgammonEngine.newGame()
                val next = accepted(initial, GameAction.OpeningRoll(DiceRoll(white, black)))
                if (white == black) {
                    assertSame(initial, next)
                    assertEquals(TurnState.Opening, next.turn)
                } else {
                    val turn = next.turn as TurnState.Moving
                    assertEquals(if (white > black) Player.WHITE else Player.BLACK, turn.player)
                    assertEquals(listOf(white, black), turn.remainingDice)
                    assertEquals(DiceRoll(white, black), turn.roll)
                }
            }
        }
    }

    @Test fun repeatedOpeningTiesRemainAwaitingOpeningUntilAnUnequalRoll() {
        var state = BackgammonEngine.newGame()
        repeat(20) { state = accepted(state, GameAction.OpeningRoll(DiceRoll(3, 3))) }
        assertEquals(TurnState.Opening, state.turn)
        state = accepted(state, GameAction.OpeningRoll(DiceRoll(2, 5)))
        assertEquals(Player.BLACK, state.currentPlayer)
    }

    @Test fun injectableOpeningDiceProduceOneAttemptWithoutAnUnboundedRerollLoop() {
        val dice = LocalDice(DieSource { 4 })
        val initial = BackgammonEngine.newGame()
        assertSame(initial, accepted(initial, dice.openingAction()))
    }

    @Test fun openingRejectsNormalRollMoveAndEndTurn() {
        val state = BackgammonEngine.newGame()
        listOf(GameAction.ApplyDice(DiceRoll(1, 2)), GameAction.Move(move(6, 5, 1)), GameAction.EndTurn)
            .forEach { assertRejected(state, it, RejectionReason.WRONG_PHASE) }
    }

    @Test fun awaitingRollRejectsOpeningMoveAndEndTurn() {
        val state = BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK)
        listOf(GameAction.OpeningRoll(DiceRoll(1, 2)), GameAction.Move(move(1, 2, 1)), GameAction.EndTurn)
            .forEach { assertRejected(state, it, RejectionReason.WRONG_PHASE) }
    }

    @Test fun activeTurnCannotRerollRepeatOpeningOrSkipLegalMoves() {
        val state = rolled(BoardState.initial(), 1, 2)
        assertRejected(state, GameAction.ApplyDice(DiceRoll(6, 6)), RejectionReason.WRONG_PHASE)
        assertRejected(state, GameAction.OpeningRoll(DiceRoll(1, 2)), RejectionReason.WRONG_PHASE)
        assertRejected(state, GameAction.EndTurn, RejectionReason.MOVES_REMAIN)
    }

    @Test fun usedDieCannotBeReusedDuringTheSameTurn() {
        val state = rolled(position(mapOf(8 to 1)), 1, 2)
        val next = accepted(state, GameAction.Move(move(8, 7, 1)))
        assertEquals(listOf(2), (next.turn as TurnState.Moving).remainingDice)
        assertRejected(next, GameAction.Move(move(7, 6, 1)), RejectionReason.ILLEGAL_MOVE)
        assertEquals(listOf(1, 2), (state.turn as TurnState.Moving).remainingDice)
    }

    @Test fun completedTurnNeedsEndTurnBeforeNextRoll() {
        var state = rolled(position(emptyMap(), mapOf(22 to 2, 12 to 13), whiteBar = 1), 1, 2)
        state = accepted(state, GameAction.Move(enter(23, 2)))
        assertTrue(state.turn is TurnState.Complete)
        listOf(GameAction.ApplyDice(DiceRoll(2, 3)), GameAction.OpeningRoll(DiceRoll(1, 2)),
            GameAction.Move(move(23, 22, 1))).forEach { assertRejected(state, it, RejectionReason.WRONG_PHASE) }
        val next = accepted(state, GameAction.EndTurn)
        assertEquals(TurnState.AwaitingRoll(Player.BLACK), next.turn)
        assertSame(state.board, next.board)
    }

    @Test fun automaticPassHandsTheRollToTheOtherPlayerExactlyOnce() {
        val board = position(emptyMap(), (19..24).associateWith { 2 } + (12 to 3), whiteBar = 1)
        val state = rolled(board, 1, 6)
        assertEquals(TurnState.AwaitingRoll(Player.BLACK), state.turn)
        assertRejected(state, GameAction.EndTurn, RejectionReason.WRONG_PHASE)
        val next = accepted(state, GameAction.ApplyDice(DiceRoll(1, 2)))
        assertEquals(Player.BLACK, next.currentPlayer)
        assertTrue(next.turn is TurnState.Moving)
    }

    @Test fun finishedGameRejectsAllPlayActions() {
        val before = rolled(position(mapOf(1 to 1)), 1, 2)
        val state = accepted(before, GameAction.Move(off(1, 2)))
        listOf(GameAction.ApplyDice(DiceRoll(1, 2)), GameAction.OpeningRoll(DiceRoll(2, 1)),
            GameAction.Move(off(1, 1)), GameAction.EndTurn).forEach {
            assertRejected(state, it, RejectionReason.WRONG_PHASE)
        }
        assertNull(state.currentPlayer)
        assertTrue(BackgammonEngine.legalSequences(state).isEmpty())
    }

    @Test fun resetWorksFromEveryPhaseAndRetainsNoOldGameData() {
        val initial = BackgammonEngine.newGame()
        val awaiting = BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK)
        val moving = rolled(BoardState.initial(), 1, 2)
        val complete = accepted(
            rolled(position(emptyMap(), mapOf(22 to 2, 12 to 13), whiteBar = 1), 1, 2),
            GameAction.Move(enter(23, 2)))
        val finished = accepted(rolled(position(mapOf(1 to 1)), 1, 2), GameAction.Move(off(1, 2)))
        listOf(initial, awaiting, moving, complete, finished).forEach {
            assertEquals(initial, accepted(it, GameAction.Reset))
        }
        assertNotEquals(initial.board, finished.board)
    }

    @Test fun resumingAFinishedPositionDetectsResultWithoutAnotherRoll() {
        val finished = accepted(rolled(position(mapOf(1 to 1)), 1, 2), GameAction.Move(off(1, 2)))
        val resumed = BackgammonEngine.fromPosition(finished.board, Player.BLACK)
        assertEquals(finished, resumed)
    }

    private fun assertRejected(state: GameState, action: GameAction, expected: RejectionReason) {
        val result = BackgammonEngine.transition(state, action) as ActionResult.Rejected
        assertEquals(expected, result.reason)
        assertSame(state, result.state)
    }
}
