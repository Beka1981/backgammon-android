package ge.gogichaishvili.backgammon.engine

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Every ordered roll, including all six doubles, exercises both movement directions. */
@RunWith(Parameterized::class)
class DiceMatrixTest(private val first: Int, private val second: Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "roll={0}-{1}")
        fun rolls(): List<Array<Int>> = (1..6).flatMap { a -> (1..6).map { b -> arrayOf(a, b) } }
    }
    private val dice get() = DiceRoll(first, second)

    @Test fun diceHaveExactlyTheRequiredUses() {
        assertEquals(first == second, dice.isDouble)
        assertEquals(if (first == second) 4 else 2, dice.uses.size)
        assertEquals(if (first == second) List(4) { first } else listOf(first, second), dice.uses)
    }

    @Test fun initialSequencesUseAllDiceAndPreserveEveryIntermediateInvariant() {
        Player.entries.forEach { player ->
            val board = BoardState.initial()
            val sequences = BackgammonRules.legalSequences(board, player, dice)
            assertTrue(sequences.isNotEmpty())
            assertEquals(sequences.size, sequences.map { it.moves }.distinct().size)
            sequences.forEach { sequence ->
                assertEquals(dice.uses.size, sequence.steps.size)
                val pool = dice.uses.toMutableList()
                sequence.steps.forEach { step ->
                    assertTrue(pool.remove(step.move.die))
                    Player.entries.forEach { assertEquals(15, step.boardAfter.total(it)) }
                    val from = (step.move.from as MoveSource.OnPoint).number
                    val to = (step.move.to as MoveDestination.OnPoint).number
                    assertEquals(player.direction * step.move.die, to - from)
                }
                assertTrue(pool.isEmpty())
            }
            assertEquals(BoardState.initial(), board)
        }
    }

    @Test fun reducerReplaysACompleteGeneratedSequenceWithoutReusingDice() {
        Player.entries.forEach { player ->
            var state = rolled(BoardState.initial(), first, second, player)
            val sequence = BackgammonEngine.legalSequences(state).first()
            val initial = state
            val earlyEnd = BackgammonEngine.transition(state, GameAction.EndTurn)
            assertEquals(RejectionReason.MOVES_REMAIN, (earlyEnd as ActionResult.Rejected).reason)
            sequence.steps.forEach { step ->
                assertTrue(BackgammonEngine.isLegalMove(state, step.move))
                state = accepted(state, GameAction.Move(step.move))
                assertEquals(step.boardAfter, state.board)
            }
            assertTrue(state.turn is TurnState.Complete)
            assertEquals(dice.uses, (initial.turn as TurnState.Moving).remainingDice)
            assertTrue(BackgammonEngine.legalMoves(state).isEmpty())
            state = accepted(state, GameAction.EndTurn)
            assertEquals(TurnState.AwaitingRoll(player.opponent), state.turn)
        }
    }

    @Test fun mirroredPositionHasExactlyMirroredLegalSequences() {
        val board = BoardState.initial()
        val white = BackgammonRules.legalSequences(board, Player.WHITE, dice)
            .map { it.moves.map(::mirror) }.toSet()
        val black = BackgammonRules.legalSequences(mirror(board), Player.BLACK, dice)
            .map { it.moves }.toSet()
        assertEquals(white, black)
    }

    @Test fun fullyBlockedBarAutomaticallyPassesForEveryRoll() {
        val board = position(emptyMap(), (19..24).associateWith { 2 } + (12 to 3), whiteBar = 1)
        assertTrue(BackgammonRules.legalSequences(board, Player.WHITE, dice).isEmpty())
        val state = rolled(board, first, second)
        assertEquals(board, state.board)
        assertEquals(TurnState.AwaitingRoll(Player.BLACK), state.turn)
    }

    @Test fun bothPlayersEnterFromTheCorrectEdgeForEveryDie() {
        val board = position(emptyMap(), whiteBar = 1)
        val white = rolled(board, first, second)
        assertEquals(setOf(enter(25 - first, first), enter(25 - second, second)),
            BackgammonEngine.legalMoves(white).toSet())
        val black = rolled(mirror(board), first, second, Player.BLACK)
        assertEquals(setOf(enter(first, first), enter(second, second)),
            BackgammonEngine.legalMoves(black).toSet())
        assertEquals(BackgammonEngine.legalSequences(white).map { it.moves.map(::mirror) }.toSet(),
            BackgammonEngine.legalSequences(black).map { it.moves }.toSet())
    }

    @Test fun injectedDiceAreExactAndReducerResultsAreDeterministic() {
        val values = ArrayDeque(listOf(first, second))
        assertEquals(dice, LocalDice(DieSource { values.removeFirst() }).roll())
        assertTrue(values.isEmpty())
        val initial = BackgammonEngine.fromPosition(BoardState.initial(), Player.WHITE)
        val action = GameAction.ApplyDice(dice)
        val a = BackgammonEngine.transition(initial, action)
        val b = BackgammonEngine.transition(initial, action)
        assertEquals(a, b)
        assertEquals(BackgammonEngine.legalSequences(a.state), BackgammonEngine.legalSequences(b.state))
        assertEquals(BackgammonEngine.legalSequences(a.state).hashCode(),
            BackgammonEngine.legalSequences(b.state).hashCode())
    }
}
