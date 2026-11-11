package ge.gogichaishvili.backgammon.designsystem

import ge.gogichaishvili.backgammon.animation.GameMotion
import ge.gogichaishvili.backgammon.engine.*
import org.junit.Assert.*
import org.junit.Test

class GameplayMotionTest {
    private fun board(w: Map<Int, Int>, b: Map<Int, Int>, whiteBar: Int = 0, blackBar: Int = 0): BoardState {
        val points = MutableList(24) { Point() }
        w.forEach { (n, count) -> points[n - 1] = Point(Player.WHITE, count) }
        b.forEach { (n, count) -> points[n - 1] = Point(Player.BLACK, count) }
        return BoardState(points, CheckerCounts(whiteBar, blackBar),
            CheckerCounts(15 - w.values.sum() - whiteBar, 15 - b.values.sum() - blackBar))
    }
    private fun moved(before: BoardState, move: CheckerMove): BoardState {
        val start = BackgammonEngine.fromPosition(before, Player.WHITE)
        val roll = (BackgammonEngine.transition(start, GameAction.ApplyDice(DiceRoll(move.die, move.die))) as ActionResult.Accepted).state
        return (BackgammonEngine.transition(roll, GameAction.Move(move)) as ActionResult.Accepted).state.board
    }
    @Test fun compressedSourceAndDestinationRestackWithoutDuplicatingAnyChecker() {
        val before = board(mapOf(8 to 8, 7 to 7), mapOf(12 to 15))
        val move = CheckerMove(MoveSource.OnPoint(8), MoveDestination.OnPoint(7), 1)
        val after = moved(before, move)
        val flights = checkerFlights(before, after, move, Player.WHITE)
        assertTrue(flights.any { it.restacking })
        assertEquals(flights.size, flights.map { it.hidden }.distinct().size)
        val finalSlots = (0 until 7).map { CheckerVisualSlot(Player.WHITE, BoardTarget.Point(8), it) } +
            (0 until 8).map { CheckerVisualSlot(Player.WHITE, BoardTarget.Point(7), it) } +
            (0 until 15).map { CheckerVisualSlot(Player.BLACK, BoardTarget.Point(12), it) }
        val suppressed = flights.map { it.hidden }.toSet()
        assertTrue(finalSlots.containsAll(suppressed))
        assertEquals(30, finalSlots.count { it !in suppressed } + flights.size)
        flights.forEach { assertEquals(checkerVisualCenter(after, it.hidden), it.to) }
        val sourceSlot = CheckerVisualSlot(Player.WHITE, BoardTarget.Point(8), 7)
        assertEquals(checkerVisualCenter(before, sourceSlot), flights.last().from)
        assertFalse(flights.last().restacking)
    }
    @Test fun hitAndCompressedBarHaveUniqueFinalSlotsAndCorrectLayerOrder() {
        val before = board(mapOf(8 to 1), mapOf(5 to 1, 12 to 6), blackBar = 8)
        val move = CheckerMove(MoveSource.OnPoint(8), MoveDestination.OnPoint(5), 3)
        val after = moved(before, move)
        val flights = checkerFlights(before, after, move, Player.WHITE)
        assertTrue(flights.any { it.restacking && it.hidden.target == BoardTarget.Bar })
        assertEquals(flights.size, flights.map { it.hidden }.distinct().size)
        assertTrue(flights[flights.lastIndex - 1].hit)
        assertEquals(Player.WHITE, flights.last().player)
        assertEquals(BoardCoordinate(500f, 422f), flights[flights.lastIndex - 1].to)
    }
    @Test fun bearingOffEndsExactlyInTheCorrectTray() {
        val before = board(mapOf(1 to 1), mapOf(12 to 15))
        val move = CheckerMove(MoveSource.OnPoint(1), MoveDestination.Off, 1)
        val after = moved(before, move)
        val flight = checkerFlights(before, after, move, Player.WHITE).last()
        assertTrue(flight.bearingOff)
        assertEquals(CheckerVisualSlot(Player.WHITE, BoardTarget.WhiteTray, 14), flight.hidden)
        assertEquals(checkerVisualCenter(after, flight.hidden), flight.to)
    }
    @Test fun visualCurvesClampAndSettleExactlyWithoutMovingGameState() {
        for (t in listOf(-1f, 0f, 1f, 2f)) {
            val end = t >= 1
            assertEquals(if (end) 1f else 0f, GameMotion.flight(t, false, false), 0f)
            assertEquals(0f, GameMotion.lift(t), 0f)
            assertEquals(0f, GameMotion.diceBounce(t), 0f)
        }
        assertEquals(720f, GameMotion.diceRotation(0f, 0), 0f)
        assertEquals(-1080f, GameMotion.diceRotation(0f, 1), 0f)
        assertEquals(0f, GameMotion.diceRotation(1f, 1), 0f)
        assertEquals(0f, GameMotion.flight(.4f, true, false), 0f)
        assertEquals(1f, GameMotion.flight(.62f, false, true), .0001f)
        (0..100).forEach { n -> assertTrue(GameMotion.diceBounce(n / 100f) in 0f..1f) }
    }
}
