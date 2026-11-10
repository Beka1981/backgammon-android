package ge.gogichaishvili.backgammon.engine

data class BoardCoordinate(val x: Float, val y: Float)
data class BoardRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
}
sealed interface BoardTarget {
    data class Point(val number: Int) : BoardTarget
    data object Bar : BoardTarget
    data object WhiteTray : BoardTarget
    data object BlackTray : BoardTarget
}

/** Fixed logical coordinates. Pixel conversion belongs exclusively to the renderer. */
object BoardGeometry {
    const val width = 1000f
    const val height = 700f
    const val aspectRatio = width / height
    const val pointWidth = 380f / 6f
    const val checkerRadius = 26f
    val bar = BoardRect(464f, 44f, 536f, 656f)
    val blackTray = BoardRect(22f, 44f, 68f, 656f)
    val whiteTray = BoardRect(932f, 44f, 978f, 656f)

    fun pointCenter(number: Int): BoardCoordinate {
        require(number in 1..24)
        val column = if (number >= 13) number - 13 else 12 - number
        val x = if (column < 6) 84f + (column + 0.5f) * pointWidth
            else 536f + (column - 6 + 0.5f) * pointWidth
        return BoardCoordinate(x, if (number >= 13) 44f else 656f)
    }

    fun pointBounds(number: Int): BoardRect {
        val c = pointCenter(number)
        return BoardRect(c.x - pointWidth / 2, if (number >= 13) 44f else 394f,
            c.x + pointWidth / 2, if (number >= 13) 306f else 656f)
    }

    fun checkerCenter(number: Int, stackIndex: Int, count: Int): BoardCoordinate {
        require(count in 1..15 && stackIndex in 0 until count)
        val c = pointCenter(number)
        val step = minOf(53f, 210f / maxOf(1, count - 1))
        val distance = checkerRadius + stackIndex * step
        return BoardCoordinate(c.x, c.y + if (number >= 13) distance else -distance)
    }

    /** Same bounds used by rendering; middle dice area is intentionally not a point. */
    fun hitTest(x: Float, y: Float): BoardTarget? = when {
        bar.contains(x, y) -> BoardTarget.Bar
        whiteTray.contains(x, y) -> BoardTarget.WhiteTray
        blackTray.contains(x, y) -> BoardTarget.BlackTray
        else -> (1..24).firstOrNull { pointBounds(it).contains(x, y) }?.let(BoardTarget::Point)
    }
}

enum class CheckerSide { IVORY, DARK }
data class CheckerStack(val point: Int, val side: CheckerSide, val count: Int) {
    init { require(point in 1..24); require(count in 1..15) }
}
/** Illustration data only. This is not a rules engine or mutable game session. */
object PreviewPosition {
    val stacks = listOf(
        CheckerStack(24, CheckerSide.IVORY, 2), CheckerStack(13, CheckerSide.IVORY, 5),
        CheckerStack(8, CheckerSide.IVORY, 3), CheckerStack(6, CheckerSide.IVORY, 5),
        CheckerStack(1, CheckerSide.DARK, 2), CheckerStack(12, CheckerSide.DARK, 5),
        CheckerStack(17, CheckerSide.DARK, 3), CheckerStack(19, CheckerSide.DARK, 5))
}
