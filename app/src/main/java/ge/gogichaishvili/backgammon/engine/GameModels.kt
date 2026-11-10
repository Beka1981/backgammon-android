package ge.gogichaishvili.backgammon.engine

/** Absolute numbering matches BoardGeometry. WHITE/ivory moves down; BLACK/dark moves up. */
enum class Player(val direction: Int, val home: IntRange) {
    WHITE(-1, 1..6), BLACK(1, 19..24);
    val opponent: Player get() = if (this == WHITE) BLACK else WHITE
    fun distanceToOff(point: Int): Int {
        require(point in 1..24)
        return if (this == WHITE) point else 25 - point
    }
    fun entryPoint(die: Int): Int {
        require(die in 1..6)
        return if (this == WHITE) 25 - die else die
    }
}

data class Point(val owner: Player? = null, val count: Int = 0) {
    init {
        require(count in 0..15) { "A point must hold 0..15 checkers" }
        require((owner == null) == (count == 0)) { "Only empty points have no owner" }
    }
}

data class CheckerCounts(val white: Int = 0, val black: Int = 0) {
    init { require(white in 0..15 && black in 0..15) }
    operator fun get(player: Player): Int = if (player == Player.WHITE) white else black
    internal fun add(player: Player, delta: Int): CheckerCounts =
        if (player == Player.WHITE) copy(white = white + delta) else copy(black = black + delta)
}

/** Defensive read-only snapshot: callers cannot mutate state through a retained list or cast. */
internal fun <T> frozenList(values: Iterable<T>): List<T> = object : AbstractList<T>() {
    private val snapshot = values.toList()
    override val size: Int get() = snapshot.size
    override fun get(index: Int): T = snapshot[index]
}

/** points[0] is point 1. Every accepted board accounts for exactly fifteen checkers per player. */
class BoardState(
    points: List<Point>,
    val bar: CheckerCounts = CheckerCounts(),
    val borneOff: CheckerCounts = CheckerCounts(),
) {
    val points: List<Point> = frozenList(points)

    init {
        require(this.points.size == 24) { "A board has exactly 24 points" }
        Player.entries.forEach { player ->
            require(total(player) == 15) { "Exactly 15 checkers per player are required" }
        }
        require(borneOff.white != 15 || borneOff.black != 15) { "Both players cannot win" }
    }

    fun point(number: Int): Point {
        require(number in 1..24) { "Point number must be 1..24" }
        return points[number - 1]
    }
    fun total(player: Player): Int =
        points.filter { it.owner == player }.sumOf { it.count } + bar[player] + borneOff[player]

    override fun equals(other: Any?): Boolean = other is BoardState &&
        points == other.points && bar == other.bar && borneOff == other.borneOff
    override fun hashCode(): Int = 31 * (31 * points.hashCode() + bar.hashCode()) + borneOff.hashCode()
    override fun toString(): String = "BoardState(points=$points, bar=$bar, borneOff=$borneOff)"

    companion object {
        fun initial(): BoardState {
            val points = MutableList(24) { Point() }
            mapOf(24 to 2, 13 to 5, 8 to 3, 6 to 5).forEach { (number, count) ->
                points[number - 1] = Point(Player.WHITE, count)
                points[24 - number] = Point(Player.BLACK, count)
            }
            return BoardState(points)
        }
    }
}

data class DiceRoll(val first: Int, val second: Int) {
    init { require(first in 1..6 && second in 1..6) { "Dice must be 1..6" } }
    val isDouble: Boolean get() = first == second
    val uses: List<Int> get() = frozenList(if (isDouble) List(4) { first } else listOf(first, second))
}

sealed interface MoveSource {
    data object Bar : MoveSource
    data class OnPoint(val number: Int) : MoveSource {
        init { require(number in 1..24) }
    }
}
sealed interface MoveDestination {
    data object Off : MoveDestination
    data class OnPoint(val number: Int) : MoveDestination {
        init { require(number in 1..24) }
    }
}
data class CheckerMove(val from: MoveSource, val to: MoveDestination, val die: Int) {
    init { require(die in 1..6) }
}
data class MoveStep(val move: CheckerMove, val boardAfter: BoardState)

/** All distinct ordered plays are retained, even when they lead to the same final board. */
class LegalMoveSequence internal constructor(val initialBoard: BoardState, steps: List<MoveStep>) {
    val steps: List<MoveStep> = frozenList(steps)
    val moves: List<CheckerMove> get() = frozenList(steps.map { it.move })
    val finalBoard: BoardState get() = steps.lastOrNull()?.boardAfter ?: initialBoard
    override fun equals(other: Any?): Boolean = other is LegalMoveSequence &&
        initialBoard == other.initialBoard && steps == other.steps
    override fun hashCode(): Int = 31 * initialBoard.hashCode() + steps.hashCode()
    override fun toString(): String = "LegalMoveSequence(moves=$moves)"
}

enum class WinKind(val points: Int) { SINGLE(1), GAMMON(2), BACKGAMMON(3) }
data class GameResult(val winner: Player, val kind: WinKind) {
    val points: Int get() = kind.points
}

sealed interface TurnState {
    data object Opening : TurnState
    data class AwaitingRoll(val player: Player) : TurnState
    class Moving internal constructor(
        val player: Player, val roll: DiceRoll, remainingDice: List<Int>,
    ) : TurnState {
        val remainingDice: List<Int> = frozenList(remainingDice)
        init {
            require(this.remainingDice.isNotEmpty())
            val pool = roll.uses.toMutableList()
            require(this.remainingDice.all { pool.remove(it) }) { "Remaining dice must belong to the roll" }
        }
        override fun equals(other: Any?): Boolean = other is Moving &&
            player == other.player && roll == other.roll && remainingDice == other.remainingDice
        override fun hashCode(): Int = 31 * (31 * player.hashCode() + roll.hashCode()) + remainingDice.hashCode()
        override fun toString(): String = "Moving(player=$player, roll=$roll, remaining=$remainingDice)"
    }
    /** All playable dice have been consumed. EndTurn explicitly commits the turn. */
    @ConsistentCopyVisibility
    data class Complete internal constructor(val player: Player, val roll: DiceRoll) : TurnState
    @ConsistentCopyVisibility
    data class Finished internal constructor(val result: GameResult) : TurnState
}

class GameState internal constructor(val board: BoardState, val turn: TurnState) {
    val result: GameResult? get() = (turn as? TurnState.Finished)?.result
    val currentPlayer: Player? get() = when (turn) {
        is TurnState.AwaitingRoll -> turn.player
        is TurnState.Moving -> turn.player
        is TurnState.Complete -> turn.player
        else -> null
    }
    init {
        val detected = BackgammonRules.result(board)
        require(if (turn is TurnState.Finished) detected == turn.result else detected == null)
        require(turn != TurnState.Opening || board == BoardState.initial())
    }
    override fun equals(other: Any?): Boolean = other is GameState && board == other.board && turn == other.turn
    override fun hashCode(): Int = 31 * board.hashCode() + turn.hashCode()
    override fun toString(): String = "GameState(board=$board, turn=$turn)"
}

sealed interface GameAction {
    /** first is White's die, second is Black's. A tie keeps the game in Opening. */
    data class OpeningRoll(val dice: DiceRoll) : GameAction
    /** Supply authoritative results here; the reducer never generates randomness. */
    data class ApplyDice(val dice: DiceRoll) : GameAction
    data class Move(val move: CheckerMove) : GameAction
    data object EndTurn : GameAction
    data object Reset : GameAction
}
enum class RejectionReason { WRONG_PHASE, ILLEGAL_MOVE, MOVES_REMAIN }
sealed interface ActionResult {
    val state: GameState
    data class Accepted(override val state: GameState) : ActionResult
    data class Rejected(override val state: GameState, val reason: RejectionReason) : ActionResult
}
