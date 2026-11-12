package ge.gogichaishvili.backgammon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import ge.gogichaishvili.backgammon.designsystem.DiceEmphasis
import androidx.compose.ui.graphics.luminance
import ge.gogichaishvili.backgammon.designsystem.AppColors
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AccessibilityContrastTest(private val theme: BoardTheme) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun skins() = ThemeRegistry.all.map { arrayOf(it) }
        private fun contrast(a: Color, b: Color): Double {
            val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
            return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
        }
    }
    @Test fun enabledTextAndControlBoundariesMeetContrastTargets() {
        listOf(AppColors.background, AppColors.surface, AppColors.surfaceRaised).forEach { background ->
            listOf(AppColors.ink, AppColors.muted, Color(theme.palette.accent)).forEach { foreground ->
                assertTrue("Normal text: ${theme.id}", contrast(foreground, background) >= 4.5)
            }
        }
        assertTrue(contrast(AppColors.background, Color(theme.palette.accent)) >= 4.5)
        assertTrue(contrast(AppColors.outline, AppColors.surface) >= 3.0)
    }
    @Test fun dicePipsRemainReadableInEveryConsumptionStateAndStyle() {
        listOf(1f, DiceEmphasis.consumed, DiceEmphasis.unplayable).forEach { alpha ->
            listOf(theme.palette.ivory, theme.palette.accent).forEach { face ->
                val displayedFace = Color(face).copy(alpha = alpha).compositeOver(AppColors.background)
                val displayedPip = Color(theme.palette.dark).copy(alpha = alpha).compositeOver(AppColors.background)
                assertTrue("Die pips: ${theme.id} opacity=$alpha", contrast(displayedFace, displayedPip) >= 3.0)
            }
        }
    }
    @Test fun dualToneMoveOutlineHasAContrastingEdgeOnEveryPointMaterial() {
        val p = theme.palette
        listOf(p.field, p.pointLight, p.pointDark, p.frame).forEach { background ->
            assertTrue("Move outline: ${theme.id}", maxOf(
                contrast(Color(p.accent), Color(background)),
                contrast(Color(p.dark), Color(background))) >= 3.0)
        }
    }
}
