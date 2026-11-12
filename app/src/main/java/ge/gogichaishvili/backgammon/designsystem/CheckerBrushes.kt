package ge.gogichaishvili.backgammon.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import ge.gogichaishvili.backgammon.domain.theme.BoardPalette

/** Four local-coordinate brushes per renderer, not a bitmap or an unbounded radius cache.
 * Moving/bearing-off radii use an uncached brush so intermediate sizes cannot accumulate.
 */
internal class CheckerBrushes(palette: BoardPalette) {
    private val ivory = Color(palette.ivory)
    private val dark = Color(palette.dark)
    private val ivoryPoint = checkerBrush(ivory, 26f)
    private val darkPoint = checkerBrush(dark, 26f)
    private val ivoryTray = checkerBrush(ivory, 18f)
    private val darkTray = checkerBrush(dark, 18f)

    fun forChecker(color: Color, radius: Float): Brush = when {
        radius == 26f && color == ivory -> ivoryPoint
        radius == 26f && color == dark -> darkPoint
        radius == 18f && color == ivory -> ivoryTray
        radius == 18f && color == dark -> darkTray
        else -> checkerBrush(color, radius)
    }
}

internal fun checkerBrush(color: Color, radius: Float): Brush =
    Brush.radialGradient(listOf(lerp(color, Color.White, .28f), color,
        lerp(color, Color.Black, .35f)), Offset(-10f, -12f), radius * 1.6f)
