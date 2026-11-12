package ge.gogichaishvili.backgammon

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import ge.gogichaishvili.backgammon.domain.theme.BoardPalette
import ge.gogichaishvili.backgammon.domain.settings.CheckerStyle

/** Pre-Task06 renderer retained only as a pixel/measurement reference. */
internal fun DrawScope.drawReferenceChecker(center: Offset, color: Color, p: BoardPalette, style: CheckerStyle,
    radius: Float = 26f, elevation: Float = 0f) {
    drawCircle(Color(p.shadow).copy(alpha = 0.22f), radius + 4f + elevation * 5f, center + Offset(2f, 5f + elevation * 7f))
    drawCircle(Color(p.shadow), radius, center + Offset(1f, 3f))
    if (style == CheckerStyle.SCULPTED) {
        drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.28f), color,
            lerp(color, Color.Black, 0.35f)), center - Offset(10f, 12f), radius * 1.6f), radius, center)
        drawCircle(lerp(color, Color.White, 0.22f), radius - 5f, center, style = Stroke(1.5f))
        drawCircle(lerp(color, Color.Black, 0.15f), radius - 8f, center, style = Stroke(2.5f))
        drawArc(Color.White.copy(alpha = 0.24f), 210f, 100f, false,
            center - Offset(radius - 3f, radius - 3f), Size((radius - 3f) * 2, (radius - 3f) * 2),
            style = Stroke(2f))
    } else {
        drawCircle(color, radius, center)
        drawCircle(Color(p.accent).copy(alpha = 0.5f), radius - 4f, center, style = Stroke(1.5f))
    }
}
