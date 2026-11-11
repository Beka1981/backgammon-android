package ge.gogichaishvili.backgammon.data

import ge.gogichaishvili.backgammon.domain.match.MatchRecord
import ge.gogichaishvili.backgammon.engine.*
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MatchDiceMatrixTest(private val first: Int, private val second: Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "dice={0},{1}")
        fun rolls(): List<Array<Int>> = (1..6).flatMap { a -> (1..6).map { b -> arrayOf(a, b) } }
    }
    @Test fun everyRollAndDoublesUsageSurvivesSerialization() {
        val roll = DiceRoll(first, second)
        val game = accepted(BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK), GameAction.ApplyDice(roll))
        val record = MatchRecord(game, roll, (game.turn as TurnState.Moving).remainingDice, Player.BLACK)
        val restored = (MatchWire.decode(MatchWire.encode(StoredMatch.Ready(record))) as StoredMatch.Ready).record
        assertEquals(record, restored)
        assertEquals(if (first == second) 4 else 2, restored.remainingDice.size)
    }
}
