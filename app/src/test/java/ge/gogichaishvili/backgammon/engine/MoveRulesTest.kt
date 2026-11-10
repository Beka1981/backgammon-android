package ge.gogichaishvili.backgammon.engine

import org.junit.Assert.*
import org.junit.Test

class MoveRulesTest {
    @Test fun standardStartingPointsAndCountsMatchTheExistingPreview() {
        val board = BoardState.initial()
        assertEquals(mapOf(24 to 2, 13 to 5, 8 to 3, 6 to 5),
            (1..24).filter { board.point(it).owner == Player.WHITE }.associateWith { board.point(it).count })
        assertEquals(mapOf(1 to 2, 12 to 5, 17 to 3, 19 to 5),
            (1..24).filter { board.point(it).owner == Player.BLACK }.associateWith { board.point(it).count })
        Player.entries.forEach {
            assertEquals(15, board.total(it)); assertEquals(0, board.bar[it]); assertEquals(0, board.borneOff[it])
        }
        PreviewPosition.stacks.forEach { stack ->
            assertEquals(stack.count, board.point(stack.point).count)
            assertEquals(if (stack.side == CheckerSide.IVORY) Player.WHITE else Player.BLACK, board.point(stack.point).owner)
        }
    }

    @Test fun hittingTransfersExactlyOneOpponentToBarAndLeavesOriginalUntouched() {
        val board = position(mapOf(8 to 1), mapOf(5 to 1, 12 to 14))
        val state = rolled(board, 3, 1)
        val next = accepted(state, GameAction.Move(move(8, 5, 3)))
        assertEquals(Point(Player.WHITE, 1), next.board.point(5))
        assertEquals(Point(), next.board.point(8))
        assertEquals(1, next.board.bar.black)
        assertEquals(Point(Player.BLACK, 1), board.point(5))
        assertEquals(0, board.bar.black)
        assertEquals(15, next.board.total(Player.BLACK))
    }

    @Test fun ownPointCanBeStackedButOpposingMadePointIsBlocked() {
        val own = rolled(position(mapOf(8 to 1, 5 to 1)), 3, 1)
        assertTrue(BackgammonEngine.isLegalMove(own, move(8, 5, 3)))
        val enemy = rolled(position(mapOf(8 to 1), mapOf(5 to 2, 12 to 13)), 3, 1)
        assertFalse(BackgammonEngine.isLegalMove(enemy, move(8, 5, 3)))
    }

    @Test fun barEntryIsMandatoryEvenWhenOtherCheckersCouldMove() {
        val state = rolled(position(mapOf(8 to 1), whiteBar = 1), 1, 2)
        assertTrue(BackgammonEngine.legalMoves(state).all { it.from == MoveSource.Bar })
        assertFalse(BackgammonEngine.isLegalMove(state, move(8, 7, 1)))
    }

    @Test fun allBarCheckersMustEnterBeforeAnyOrdinaryMove() {
        val state = rolled(position(mapOf(8 to 1), whiteBar = 2), 1, 2)
        val next = accepted(state, GameAction.Move(enter(24, 1)))
        assertEquals(1, next.board.bar.white)
        assertEquals(listOf(enter(23, 2)), BackgammonEngine.legalMoves(next))
    }

    @Test fun enteredCheckerMayUseSecondDieAfterBarIsEmpty() {
        val state = rolled(position(emptyMap(), whiteBar = 1), 1, 2)
        val next = accepted(state, GameAction.Move(enter(24, 1)))
        assertTrue(BackgammonEngine.isLegalMove(next, move(24, 22, 2)))
    }

    @Test fun barEntryCanHitButCannotEnterAMadePoint() {
        val state = rolled(position(emptyMap(), mapOf(24 to 1, 23 to 2, 12 to 12), whiteBar = 1), 1, 2)
        assertEquals(listOf(enter(24, 1)), BackgammonEngine.legalMoves(state))
        val next = accepted(state, GameAction.Move(enter(24, 1)))
        assertEquals(1, next.board.bar.black)
        assertEquals(0, next.board.bar.white)
    }

    @Test fun onlyTheHigherDieMayBeUsedWhenEitherButNotBothCanPlay() {
        val state = rolled(position(emptyMap(), mapOf(22 to 2, 12 to 13), whiteBar = 1), 1, 2)
        assertEquals(listOf(enter(23, 2)), BackgammonEngine.legalMoves(state))
        assertFalse(BackgammonEngine.isLegalMove(state, enter(24, 1)))
        val next = accepted(state, GameAction.Move(enter(23, 2)))
        assertTrue(next.turn is TurnState.Complete)
    }

    @Test fun lowerDieRemainsLegalWhenHigherCannotPlay() {
        val state = rolled(position(emptyMap(), mapOf(23 to 2, 22 to 2, 12 to 11), whiteBar = 1), 1, 2)
        assertEquals(listOf(enter(24, 1)), BackgammonEngine.legalMoves(state))
    }

    @Test fun maximumDiceUsageRejectsAnOtherwiseLegalFirstMove() {
        val state = rolled(position(mapOf(6 to 1, 5 to 1), mapOf(1 to 2, 12 to 13)), 6, 4)
        assertEquals(listOf(move(6, 2, 4)), BackgammonEngine.legalMoves(state))
        assertFalse(BackgammonEngine.isLegalMove(state, off(6, 6)))
        assertEquals(listOf(move(6, 2, 4), off(5, 6)),
            BackgammonEngine.legalSequences(state).single().moves)
    }

    @Test fun bothDiceOrdersAndTheirIntermediateBoardsAreReturned() {
        val board = position(mapOf(8 to 1))
        val sequences = BackgammonRules.legalSequences(board, Player.WHITE, DiceRoll(1, 2))
        assertEquals(setOf(
            listOf(move(8, 7, 1), move(7, 5, 2)),
            listOf(move(8, 6, 2), move(6, 5, 1))), sequences.map { it.moves }.toSet())
        assertEquals(1, sequences.first { it.moves.first().die == 1 }.steps.first().boardAfter.point(7).count)
        assertEquals(1, sequences.first { it.moves.first().die == 2 }.steps.first().boardAfter.point(6).count)
        assertEquals(1, sequences.map { it.finalBoard }.distinct().size)
    }

    @Test fun jumpingOverBlockedPointsIsAllowedButLandingOnThemIsNot() {
        val state = rolled(position(mapOf(8 to 1), mapOf(7 to 2, 12 to 13)), 1, 2)
        assertTrue(BackgammonEngine.isLegalMove(state, move(8, 6, 2)))
        assertFalse(BackgammonEngine.isLegalMove(state, move(8, 7, 1)))
    }

    @Test fun sumOfDiceCannotJumpOverTwoBlockedIntermediateLandings() {
        val state = rolled(position(mapOf(8 to 1), mapOf(7 to 2, 6 to 2, 12 to 11)), 1, 2)
        assertTrue(state.turn is TurnState.AwaitingRoll)
        assertTrue(BackgammonEngine.legalMoves(state).isEmpty())
        assertFalse(BackgammonEngine.isLegalMove(state, move(8, 5, 3)))
    }

    @Test fun doublesUseFourMovesWithoutAnExtraTurn() {
        var state = rolled(position(mapOf(20 to 1), mapOf(1 to 15)), 2, 2)
        val moves = listOf(move(20, 18, 2), move(18, 16, 2), move(16, 14, 2), move(14, 12, 2))
        assertEquals(moves, BackgammonEngine.legalSequences(state).single().moves)
        moves.forEach { state = accepted(state, GameAction.Move(it)) }
        assertTrue(state.turn is TurnState.Complete)
        assertEquals(TurnState.AwaitingRoll(Player.BLACK), accepted(state, GameAction.EndTurn).turn)
    }

    @Test fun doublesUseOnlyTheMaximumPlayableCountWhenBlocked() {
        var state = rolled(position(emptyMap(), mapOf(21 to 2, 12 to 13), whiteBar = 1), 2, 2)
        assertEquals(1, BackgammonEngine.legalSequences(state).single().steps.size)
        state = accepted(state, GameAction.Move(enter(23, 2)))
        assertTrue(state.turn is TurnState.Complete)
    }

    @Test fun partialDoublesRequireTwoOrThreeMovesWhenThoseAreTheMaximum() {
        listOf(8 to 2, 10 to 3).forEach { (start, expectedCount) ->
            val state = rolled(position(mapOf(start to 1), mapOf(2 to 2, 12 to 13)), 2, 2)
            val sequence = BackgammonEngine.legalSequences(state).single()
            assertEquals(expectedCount, sequence.steps.size)
            var next = state
            sequence.steps.forEach { next = accepted(next, GameAction.Move(it.move)) }
            assertTrue(next.turn is TurnState.Complete)
            assertEquals(Point(Player.WHITE, 1), next.board.point(4))
        }
    }

    @Test fun movingOpponentBackwardOrWithAnUnrolledDieIsRejected() {
        val state = rolled(BoardState.initial(), 1, 2)
        listOf(move(1, 2, 1), move(6, 7, 1), move(8, 5, 3), move(10, 9, 1)).forEach {
            val result = BackgammonEngine.transition(state, GameAction.Move(it)) as ActionResult.Rejected
            assertSame(state, result.state)
            assertEquals(RejectionReason.ILLEGAL_MOVE, result.reason)
        }
    }
}
