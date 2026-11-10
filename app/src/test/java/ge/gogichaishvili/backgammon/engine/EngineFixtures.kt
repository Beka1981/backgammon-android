package ge.gogichaishvili.backgammon.engine

internal fun position(
    white: Map<Int, Int>,
    black: Map<Int, Int> = mapOf(12 to 15),
    whiteBar: Int = 0,
    blackBar: Int = 0,
): BoardState {
    require(white.keys.intersect(black.keys).isEmpty())
    val points = MutableList(24) { Point() }
    white.forEach { (p, n) -> points[p - 1] = Point(Player.WHITE, n) }
    black.forEach { (p, n) -> points[p - 1] = Point(Player.BLACK, n) }
    return BoardState(points, CheckerCounts(whiteBar, blackBar),
        CheckerCounts(15 - white.values.sum() - whiteBar, 15 - black.values.sum() - blackBar))
}

internal fun move(from: Int, to: Int, die: Int) =
    CheckerMove(MoveSource.OnPoint(from), MoveDestination.OnPoint(to), die)
internal fun off(from: Int, die: Int) = CheckerMove(MoveSource.OnPoint(from), MoveDestination.Off, die)
internal fun enter(to: Int, die: Int) = CheckerMove(MoveSource.Bar, MoveDestination.OnPoint(to), die)
internal fun accepted(state: GameState, action: GameAction): GameState =
    (BackgammonEngine.transition(state, action) as ActionResult.Accepted).state
internal fun rolled(board: BoardState, first: Int, second: Int, player: Player = Player.WHITE): GameState =
    accepted(BackgammonEngine.fromPosition(board, player), GameAction.ApplyDice(DiceRoll(first, second)))
internal fun mirror(board: BoardState) = BoardState(
    board.points.reversed().map { if (it.owner == null) it else Point(it.owner.opponent, it.count) },
    CheckerCounts(board.bar.black, board.bar.white), CheckerCounts(board.borneOff.black, board.borneOff.white))
internal fun mirror(move: CheckerMove): CheckerMove = CheckerMove(
    when (val from = move.from) {
        MoveSource.Bar -> MoveSource.Bar
        is MoveSource.OnPoint -> MoveSource.OnPoint(25 - from.number)
    },
    when (val to = move.to) {
        MoveDestination.Off -> MoveDestination.Off
        is MoveDestination.OnPoint -> MoveDestination.OnPoint(25 - to.number)
    }, move.die)
