package ge.gogichaishvili.backgammon.designsystem

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.*
import ge.gogichaishvili.backgammon.engine.*

/** Stateless renderer. Overlay slot shares the board bounds for future animated pieces. */
@Composable
fun BoardCanvas(
    theme: BoardTheme,
    modifier: Modifier = Modifier,
    checkerStyle: CheckerStyle = CheckerStyle.SCULPTED,
    diceStyle: DiceStyle = DiceStyle.IVORY,
    description: String? = null,
    board: BoardState = BoardState.initial(),
    diceValues: List<Int> = listOf(3, 5),
    hidden: List<CheckerVisualSlot> = emptyList(),
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    CompositionTrace("BG.Compose.BoardCanvas")
    val palette = theme.palette
    val checkerBrushes = remember(palette) { CheckerBrushes(palette) }
    val labelPaint = remember(palette) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(palette.accent).toArgb()
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }
    }
    val semantics = if (description == null) Modifier else Modifier.semantics {
        contentDescription = description
        role = Role.Image
    }
    Box(modifier.aspectRatio(BoardGeometry.aspectRatio).then(semantics)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            scale(size.width / BoardGeometry.width, size.height / BoardGeometry.height, Offset.Zero) {
                renderTrace("BG.Draw.Materials") { drawBoard(theme, labelPaint) }
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            scale(size.width / BoardGeometry.width, size.height / BoardGeometry.height, Offset.Zero) {
                renderTrace("BG.Draw.Checkers") { drawPosition(board, hidden, palette, checkerStyle, checkerBrushes) }
                diceValues.take(2).forEachIndexed { index, value ->
                    renderTrace("BG.Draw.BoardDice") { drawDie(Offset(625f + index * 77f, 325f), value, palette, diceStyle) }
                }
            }
        }
        overlay()
    }
}

private fun DrawScope.drawBoard(
    theme: BoardTheme,
    labelPaint: Paint,
) {
    val p = theme.palette
    drawRoundRect(
        Brush.linearGradient(listOf(Color(p.frameLight), Color(p.frame), Color(p.frameLight))),
        size = Size(1000f, 700f), cornerRadius = CornerRadius(28f))
    drawRoundRect(Color(p.accent).copy(alpha = 0.6f), Offset(10f, 10f),
        Size(980f, 680f), CornerRadius(20f), style = Stroke(2f))
    // Original procedural grain; no external artwork.
    if (theme.id == ThemeRegistry.classic.id) {
        repeat(24) { i ->
            val y = 18f + i * 28f
            drawLine(Color(p.accent).copy(alpha = 0.07f), Offset(16f, y), Offset(984f, y + 7f), 2f)
        }
    }
    listOf(BoardRect(84f, 44f, 464f, 656f), BoardRect(536f, 44f, 916f, 656f)).forEach { r ->
        drawRect(Brush.verticalGradient(listOf(Color(p.field), Color(p.field).copy(alpha = 0.82f), Color(p.field))),
            Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top))
    }
    listOf(BoardGeometry.blackTray, BoardGeometry.whiteTray, BoardGeometry.bar).forEach { r ->
        drawRoundRect(Color(p.frame), Offset(r.left, r.top),
            Size(r.right - r.left, r.bottom - r.top), CornerRadius(8f))
        drawRoundRect(Color(p.accent).copy(alpha = 0.35f), Offset(r.left, r.top),
            Size(r.right - r.left, r.bottom - r.top), CornerRadius(8f), style = Stroke(1.5f))
    }
    repeat(15) { i ->
        val y = 62f + i * 40f
        drawLine(Color(p.accent).copy(alpha = 0.18f), Offset(24f, y), Offset(66f, y))
        drawLine(Color(p.accent).copy(alpha = 0.18f), Offset(934f, y), Offset(976f, y))
    }
    (1..24).forEach { point ->
        val c = BoardGeometry.pointCenter(point)
        val top = point >= 13
        val path = Path().apply {
            moveTo(c.x - BoardGeometry.pointWidth / 2 + 2f, c.y)
            lineTo(c.x + BoardGeometry.pointWidth / 2 - 2f, c.y)
            lineTo(c.x, if (top) 304f else 396f)
            close()
        }
        drawPath(path, Color(if (point % 2 == 0) p.pointDark else p.pointLight))
        drawPath(path, Color(p.accent).copy(alpha = 0.2f), style = Stroke(1f))
        drawContext.canvas.nativeCanvas.drawText(point.toString(), c.x, if (top) 31f else 687f, labelPaint)
    }
    // Hinges emphasize the bar without modifying the logical playfield.
    listOf(160f, 520f).forEach { y ->
        drawRoundRect(Brush.horizontalGradient(listOf(Color(p.frame), Color(p.accent), Color(p.frame))),
            Offset(486f, y), Size(28f, 24f), CornerRadius(4f))
    }
}

private fun DrawScope.drawPosition(board: BoardState, hidden: List<CheckerVisualSlot>, p: BoardPalette, style: CheckerStyle, brushes: CheckerBrushes) {
    fun render(slot: CheckerVisualSlot, radius: Float = BoardGeometry.checkerRadius) {
        if (slot in hidden) return
        val c = checkerVisualCenter(board, slot)
        drawChecker(Offset(c.x, c.y), Color(if (slot.player == Player.WHITE) p.ivory else p.dark), p, style, radius, brushes = brushes)
    }
    (1..24).forEach { number ->
        val point = board.point(number)
        val owner = point.owner
        if (owner != null) repeat(point.count) { render(CheckerVisualSlot(owner, BoardTarget.Point(number), it)) }
    }
    Player.entries.forEach { player ->
        repeat(board.bar[player]) { render(CheckerVisualSlot(player, BoardTarget.Bar, it)) }
        repeat(board.borneOff[player]) { render(CheckerVisualSlot(player,
            if (player == Player.WHITE) BoardTarget.WhiteTray else BoardTarget.BlackTray, it), 18f) }
    }
}

internal fun DrawScope.drawChecker(center: Offset, color: Color, p: BoardPalette, style: CheckerStyle,
    radius: Float = BoardGeometry.checkerRadius, elevation: Float = 0f, brushes: CheckerBrushes? = null) {
    translate(center.x, center.y) {
        val localCenter = Offset.Zero
        drawCircle(Color(p.shadow).copy(alpha = 0.22f), radius + 4f + elevation * 5f, localCenter + Offset(2f, 5f + elevation * 7f))
        drawCircle(Color(p.shadow), radius, localCenter + Offset(1f, 3f))
        if (style == CheckerStyle.SCULPTED) {
            drawCircle(brushes?.forChecker(color, radius) ?: checkerBrush(color, radius), radius, localCenter)
            drawCircle(lerp(color, Color.White, 0.22f), radius - 5f, localCenter, style = Stroke(1.5f))
            drawCircle(lerp(color, Color.Black, 0.15f), radius - 8f, localCenter, style = Stroke(2.5f))
            drawArc(Color.White.copy(alpha = 0.24f), 210f, 100f, false,
                localCenter - Offset(radius - 3f, radius - 3f), Size((radius - 3f) * 2, (radius - 3f) * 2),
                style = Stroke(2f))
        } else {
            drawCircle(color, radius, localCenter)
            drawCircle(Color(p.accent).copy(alpha = 0.5f), radius - 4f, localCenter, style = Stroke(1.5f))
        }
    }
}

internal fun DrawScope.drawDie(origin: Offset, value: Int, p: BoardPalette, style: DiceStyle) {
    val face = Color(if (style == DiceStyle.IVORY) p.ivory else p.accent)
    drawRoundRect(Color(p.shadow), origin + Offset(2f, 5f), Size(52f, 52f), CornerRadius(9f))
    drawRoundRect(Brush.linearGradient(listOf(lerp(face, Color.White, 0.2f), face)),
        origin, Size(52f, 52f), CornerRadius(9f))
    val pips = diePips.getOrNull(value) ?: return
    for (i in pips.indices) drawCircle(Color(p.dark), 4f, origin + pips[i])
}
