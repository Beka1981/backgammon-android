package ge.gogichaishvili.backgammon.engine

import org.junit.Assert.*
import org.junit.Test

class BearOffAndScoringTest {
    @Test fun exactBearingOffIsAllowedEvenWithHigherCheckersPresent() {
        val state = rolled(position(mapOf(4 to 1, 6 to 1)), 4, 4)
        assertTrue(BackgammonEngine.isLegalMove(state, off(4, 4)))
    }

    @Test fun oversizedDieCanOnlyRemoveTheFarthestChecker() {
        val state = rolled(position(mapOf(3 to 1, 5 to 1)), 6, 6)
        assertEquals(listOf(off(5, 6)), BackgammonEngine.legalMoves(state))
        val next = accepted(state, GameAction.Move(off(5, 6)))
        assertEquals(listOf(off(3, 6)), BackgammonEngine.legalMoves(next))
    }

    @Test fun exactLowerDieIsAllowedWhileOversizedDieCannotRemoveALowerChecker() {
        val state = rolled(position(mapOf(2 to 1, 5 to 1)), 2, 6)
        assertTrue(BackgammonEngine.isLegalMove(state, off(2, 2)))
        assertFalse(BackgammonEngine.isLegalMove(state, off(2, 6)))
    }

    @Test fun outsideCheckerPreventsBearingOffUntilItEntersHomeDuringTheTurn() {
        val state = rolled(position(mapOf(7 to 1, 1 to 1)), 1, 6)
        assertFalse(BackgammonRules.canBearOff(state.board, Player.WHITE))
        assertFalse(BackgammonEngine.isLegalMove(state, off(1, 1)))
        val next = accepted(state, GameAction.Move(move(7, 6, 1)))
        assertTrue(BackgammonRules.canBearOff(next.board, Player.WHITE))
        assertTrue(BackgammonEngine.isLegalMove(next, off(6, 6)))
    }

    @Test fun aHitCheckerOnBarPreventsBearingOffEvenIfOtherCheckersAreHome() {
        val state = rolled(position(mapOf(1 to 1), whiteBar = 1), 6, 1)
        assertFalse(BackgammonRules.canBearOff(state.board, Player.WHITE))
        assertFalse(BackgammonEngine.isLegalMove(state, off(1, 1)))
        val next = accepted(state, GameAction.Move(enter(19, 6)))
        assertFalse(BackgammonRules.canBearOff(next.board, Player.WHITE))
    }

    @Test fun canChooseHomeBoardMovementInsteadOfBearingOffWhenBothDiceRemainUsable() {
        val state = rolled(position(mapOf(6 to 2)), 1, 2)
        assertTrue(BackgammonEngine.isLegalMove(state, move(6, 5, 1)))
        assertTrue(BackgammonEngine.isLegalMove(state, move(6, 4, 2)))
    }

    @Test fun mandatoryUsageCanRequireMovingBeforeTheLastCheckerIsBorneOff() {
        val state = rolled(position(mapOf(2 to 1)), 1, 2)
        assertEquals(listOf(move(2, 1, 1)), BackgammonEngine.legalMoves(state))
        assertEquals(listOf(move(2, 1, 1), off(1, 2)), BackgammonEngine.legalSequences(state).single().moves)
    }

    @Test fun winningWithUnusedDoublesFinishesImmediately() {
        val state = rolled(position(mapOf(6 to 1)), 2, 2)
        assertEquals(3, BackgammonEngine.legalSequences(state).single().steps.size)
        var next = state
        BackgammonEngine.legalSequences(state).single().moves.forEach {
            next = accepted(next, GameAction.Move(it))
        }
        assertEquals(15, next.board.borneOff.white)
        assertTrue(next.turn is TurnState.Finished)
        assertTrue(BackgammonEngine.legalMoves(next).isEmpty())
    }

    @Test fun singleWinWhenLoserHasBorneOffAnyCheckerEvenWithAnotherOnBar() {
        val state = rolled(position(mapOf(1 to 1), mapOf(12 to 13), blackBar = 1), 1, 2)
        val next = accepted(state, GameAction.Move(off(1, 2)))
        assertEquals(GameResult(Player.WHITE, WinKind.SINGLE), next.result)
        assertEquals(1, next.result!!.points)
    }

    @Test fun gammonWhenLoserHasNoneOffAndNoneInWinnersHomeOrOnBar() {
        val next = accepted(rolled(position(mapOf(1 to 1)), 1, 2), GameAction.Move(off(1, 2)))
        assertEquals(GameResult(Player.WHITE, WinKind.GAMMON), next.result)
        assertEquals(2, next.result!!.points)
    }

    @Test fun backgammonWhenLoserHasCheckerOnBar() {
        val next = accepted(rolled(position(mapOf(1 to 1), mapOf(12 to 14), blackBar = 1), 1, 2),
            GameAction.Move(off(1, 2)))
        assertEquals(GameResult(Player.WHITE, WinKind.BACKGAMMON), next.result)
        assertEquals(3, next.result!!.points)
    }

    @Test fun backgammonWhenLoserHasCheckerInWinnersHome() {
        val next = accepted(rolled(position(mapOf(1 to 1), mapOf(2 to 1, 12 to 14)), 1, 2),
            GameAction.Move(off(1, 2)))
        assertEquals(GameResult(Player.WHITE, WinKind.BACKGAMMON), next.result)
    }

    @Test fun blackBearingOffAndAllThreeScoresMirrorWhiteExactly() {
        listOf(
            position(mapOf(1 to 1), mapOf(12 to 14)),
            position(mapOf(1 to 1)),
            position(mapOf(1 to 1), mapOf(2 to 1, 12 to 14)),
            position(mapOf(1 to 1), mapOf(12 to 14), blackBar = 1),
        ).forEach { board ->
            val white = accepted(rolled(board, 1, 2), GameAction.Move(off(1, 2)))
            val black = accepted(rolled(mirror(board), 1, 2, Player.BLACK), GameAction.Move(off(24, 2)))
            assertEquals(white.result!!.kind, black.result!!.kind)
            assertEquals(Player.BLACK, black.result!!.winner)
            assertEquals(mirror(white.board), black.board)
        }
    }

    @Test fun loserInOwnHomeDoesNotCauseBackgammon() {
        val next = accepted(rolled(position(mapOf(1 to 1), mapOf(19 to 15)), 1, 2),
            GameAction.Move(off(1, 2)))
        assertEquals(WinKind.GAMMON, next.result!!.kind)
    }

    @Test fun unfinishedPositionHasNoResult() {
        assertNull(BackgammonRules.result(BoardState.initial()))
    }
}
