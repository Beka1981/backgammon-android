package ge.gogichaishvili.backgammon.engine

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class EngineInvariantTest {
    @Test fun invalidDiceAndInvalidSourcesFailFast() {
        listOf(-1, 0, 7, Int.MAX_VALUE).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { DiceRoll(value, 1) }
            assertThrows(IllegalArgumentException::class.java) { DiceRoll(1, value) }
            assertThrows(IllegalArgumentException::class.java) { LocalDice(DieSource { value }).roll() }
        }
    }

    @Test fun invalidPointOwnersAndCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Point(null, 1) }
        assertThrows(IllegalArgumentException::class.java) { Point(Player.WHITE, 0) }
        assertThrows(IllegalArgumentException::class.java) { Point(Player.BLACK, -1) }
        assertThrows(IllegalArgumentException::class.java) { Point(Player.WHITE, 16) }
        assertThrows(IllegalArgumentException::class.java) { CheckerCounts(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { CheckerCounts(0, 16) }
    }

    @Test fun invalidBoardSizeTotalsAndTwoWinnersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BoardState(List(23) { Point() }) }
        assertThrows(IllegalArgumentException::class.java) { BoardState(List(24) { Point() }) }
        assertThrows(IllegalArgumentException::class.java) {
            BoardState(List(24) { Point() }, borneOff = CheckerCounts(15, 15))
        }
        val points = BoardState.initial().points.toMutableList()
        points[23] = Point(Player.WHITE, 1)
        assertThrows(IllegalArgumentException::class.java) { BoardState(points) }
    }

    @Test fun invalidMoveShapesAndPointLookupsAreRejected() {
        listOf(0, 25).forEach {
            assertThrows(IllegalArgumentException::class.java) { MoveSource.OnPoint(it) }
            assertThrows(IllegalArgumentException::class.java) { MoveDestination.OnPoint(it) }
            assertThrows(IllegalArgumentException::class.java) { BoardState.initial().point(it) }
        }
        listOf(0, 7).forEach {
            assertThrows(IllegalArgumentException::class.java) { move(6, 5, it) }
        }
    }

    @Test fun boardDefensivelyCopiesInputsAndDoesNotExposeMutableCollections() {
        val input = BoardState.initial().points.toMutableList()
        val board = BoardState(input)
        input[23] = Point()
        assertEquals(Point(Player.WHITE, 2), board.point(24))
        assertThrows(RuntimeException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (board.points as MutableList<Point>)[23] = Point()
        }
        assertEquals(BoardState.initial(), board)
    }

    @Test fun turnAndGeneratedSequencesDoNotExposeMutableCollections() {
        val input = mutableListOf(1, 2)
        val turn = TurnState.Moving(Player.WHITE, DiceRoll(1, 2), input)
        input.clear()
        assertEquals(listOf(1, 2), turn.remainingDice)
        assertThrows(RuntimeException::class.java) { (turn.remainingDice as MutableList<Int>).clear() }
        val state = rolled(position(mapOf(8 to 1)), 1, 2)
        val sequences = BackgammonEngine.legalSequences(state)
        assertThrows(RuntimeException::class.java) { (sequences as MutableList<LegalMoveSequence>).clear() }
        assertThrows(RuntimeException::class.java) { (sequences.first().steps as MutableList<MoveStep>).clear() }
        assertThrows(RuntimeException::class.java) { (sequences.first().moves as MutableList<CheckerMove>).clear() }
        assertEquals(2, sequences.first().steps.size)
    }

    @Test fun impossibleRemainingDiceAndInconsistentGamePhasesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TurnState.Moving(Player.WHITE, DiceRoll(1, 2), listOf(1, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState.Moving(Player.WHITE, DiceRoll(1, 2), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(BoardState.initial(), TurnState.Finished(GameResult(Player.WHITE, WinKind.GAMMON)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(position(mapOf(1 to 1)), TurnState.Opening)
        }
    }

    @Test fun deterministicSeededGamesPreserveInvariantsThroughCompletion() {
        // Several complete races exercise transitions beyond handcrafted opening/bear-off fixtures.
        listOf(7, 19, 41).forEach { seed ->
            val random = Random(seed)
            val dice = LocalDice(DieSource { random.nextInt(1, 7) })
            var state = BackgammonEngine.newGame()
            var actions = 0
            while (state.result == null && actions++ < 10000) {
                val before = state
                val boardSnapshot = before.board.points.toList()
                val action = when (state.turn) {
                    TurnState.Opening -> dice.openingAction()
                    is TurnState.AwaitingRoll -> GameAction.ApplyDice(dice.roll())
                    is TurnState.Moving -> {
                        val choices = BackgammonEngine.legalMoves(state)
                        assertTrue(choices.isNotEmpty())
                        GameAction.Move(choices[random.nextInt(choices.size)])
                    }
                    is TurnState.Complete -> GameAction.EndTurn
                    is TurnState.Finished -> error("Already finished")
                }
                state = accepted(state, action)
                Player.entries.forEach { player ->
                    assertEquals(15, state.board.total(player))
                    assertTrue(state.board.bar[player] >= 0)
                    assertTrue(state.board.borneOff[player] >= 0)
                }
                assertEquals(boardSnapshot, before.board.points)
            }
            assertNotNull("Seed $seed did not finish within the action budget", state.result)
            assertEquals(15, state.board.borneOff[state.result!!.winner])
            assertEquals(state.result, BackgammonRules.result(state.board))
        }
    }
}
