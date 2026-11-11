package ge.gogichaishvili.backgammon.designsystem

import ge.gogichaishvili.backgammon.engine.*

data class CheckerVisualSlot(val player: Player, val target: BoardTarget, val index: Int)
data class CheckerFlight(
    val player: Player, val from: BoardCoordinate, val to: BoardCoordinate,
    val hidden: CheckerVisualSlot, val bearingOff: Boolean = false, val hit: Boolean = false,
    val restacking: Boolean = false,
)

/** Presentation geometry only. No legality or authoritative state changes. */
fun checkerVisualCenter(board: BoardState, slot: CheckerVisualSlot): BoardCoordinate = when (val target = slot.target) {
    is BoardTarget.Point -> BoardGeometry.checkerCenter(target.number, slot.index, board.point(target.number).count)
    BoardTarget.Bar -> {
        val count = board.bar[slot.player]
        val distance = slot.index * minOf(42f, 200f / maxOf(1, count - 1))
        BoardCoordinate(500f, if (slot.player == Player.WHITE) 78f + distance else 622f - distance)
    }
    BoardTarget.WhiteTray -> BoardCoordinate(955f, 64f + slot.index * 40f)
    BoardTarget.BlackTray -> BoardCoordinate(45f, 64f + slot.index * 40f)
}

fun checkerFlights(before: BoardState, after: BoardState, move: CheckerMove, player: Player): List<CheckerFlight> {
    val source = when (val from = move.from) {
        MoveSource.Bar -> CheckerVisualSlot(player, BoardTarget.Bar, before.bar[player] - 1)
        is MoveSource.OnPoint -> CheckerVisualSlot(player, BoardTarget.Point(from.number), before.point(from.number).count - 1)
    }
    val destination = when (val to = move.to) {
        MoveDestination.Off -> CheckerVisualSlot(player,
            if (player == Player.WHITE) BoardTarget.WhiteTray else BoardTarget.BlackTray, after.borneOff[player] - 1)
        is MoveDestination.OnPoint -> CheckerVisualSlot(player, BoardTarget.Point(to.number), after.point(to.number).count - 1)
    }
    val moving = CheckerFlight(player, checkerVisualCenter(before, source), checkerVisualCenter(after, destination),
        destination, bearingOff = move.to == MoveDestination.Off)
    val target = move.to as? MoveDestination.OnPoint
    val opponent = player.opponent
    val hit = if (target != null && after.bar[opponent] > before.bar[opponent]) {
        val end = CheckerVisualSlot(opponent, BoardTarget.Bar, after.bar[opponent] - 1)
        CheckerFlight(opponent,
            checkerVisualCenter(before, CheckerVisualSlot(opponent, BoardTarget.Point(target.number), 0)),
            checkerVisualCenter(after, end), end, hit = true)
    } else null
    // Animate existing stack members when compression changes, hiding each final slot exactly once.
    val settling = buildList {
        Player.entries.forEach { owner ->
            val targets = (1..24).map { BoardTarget.Point(it) } + listOf(BoardTarget.Bar,
                if (owner == Player.WHITE) BoardTarget.WhiteTray else BoardTarget.BlackTray)
            fun count(board: BoardState, target: BoardTarget): Int = when (target) {
                is BoardTarget.Point -> board.point(target.number).let { if (it.owner == owner) it.count else 0 }
                BoardTarget.Bar -> board.bar[owner]
                else -> board.borneOff[owner]
            }
            targets.forEach { location ->
                repeat(minOf(count(before, location), count(after, location))) { index ->
                    val slot = CheckerVisualSlot(owner, location, index)
                    val from = checkerVisualCenter(before, slot)
                    val to = checkerVisualCenter(after, slot)
                    if (from != to) add(CheckerFlight(owner, from, to, slot, restacking = true))
                }
            }
        }
    }
    // Stack members below the hit checker, arriving checker above both.
    return settling + listOfNotNull(hit, moving)
}
