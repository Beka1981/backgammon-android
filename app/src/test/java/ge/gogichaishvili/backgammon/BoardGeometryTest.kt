package ge.gogichaishvili.backgammon

import ge.gogichaishvili.backgammon.engine.*
import org.junit.Assert.*
import org.junit.Test

class BoardGeometryTest {
    @Test fun all24PointsHaveDistinctCentersAndRoundTripHitTargets() {
        val centers = (1..24).map(BoardGeometry::pointCenter)
        assertEquals(24, centers.distinct().size)
        (1..24).forEach { point ->
            val c = BoardGeometry.checkerCenter(point, 0, 1)
            assertEquals(BoardTarget.Point(point), BoardGeometry.hitTest(c.x, c.y))
        }
    }
    @Test fun numberingFollowsOpposingDirectionsAndMirrorsAcrossBoard() {
        assertTrue(BoardGeometry.pointCenter(13).x < BoardGeometry.pointCenter(24).x)
        assertTrue(BoardGeometry.pointCenter(12).x < BoardGeometry.pointCenter(1).x)
        (1..12).forEach {
            assertEquals(BoardGeometry.pointCenter(it).x, BoardGeometry.pointCenter(25 - it).x)
        }
    }
    @Test fun barTraysAndDiceAreaAreDistinctFromPoints() {
        assertEquals(BoardTarget.Bar, BoardGeometry.hitTest(500f, 350f))
        assertEquals(BoardTarget.WhiteTray, BoardGeometry.hitTest(950f, 350f))
        assertEquals(BoardTarget.BlackTray, BoardGeometry.hitTest(45f, 350f))
        assertNull(BoardGeometry.hitTest(650f, 350f))
        assertNull(BoardGeometry.hitTest(-1f, 350f))
    }
    @Test fun everyStackSizeRemainsWithinItsPointRegion() {
        (1..24).forEach { point ->
            (1..15).forEach { count ->
                repeat(count) { i ->
                    val c = BoardGeometry.checkerCenter(point, i, count)
                    val bounds = BoardGeometry.pointBounds(point)
                    assertTrue(bounds.contains(c.x, c.y - BoardGeometry.checkerRadius))
                    assertTrue(bounds.contains(c.x, c.y + BoardGeometry.checkerRadius))
                }
            }
        }
    }
    @Test fun startingPositionHas15CheckersPerSideAndNoSharedPoints() {
        CheckerSide.entries.forEach { side ->
            assertEquals(15, PreviewPosition.stacks.filter { it.side == side }.sumOf { it.count })
        }
        assertEquals(8, PreviewPosition.stacks.map { it.point }.distinct().size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidPointsAreRejected() { BoardGeometry.pointCenter(0) }
}
