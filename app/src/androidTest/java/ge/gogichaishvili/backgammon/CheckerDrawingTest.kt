package ge.gogichaishvili.backgammon

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.domain.settings.CheckerStyle
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CheckerDrawingTest {
    @Test fun cachedCheckerPreservesPixelsForEverySkinStyleAndTrayOrFlightRadius() {
        ThemeRegistry.all.forEach { theme ->
            val brushes = CheckerBrushes(theme.palette)
            listOf(theme.palette.ivory, theme.palette.dark).forEach { argb ->
                val color = Color(argb)
                assertSame(brushes.forChecker(color, 26f), brushes.forChecker(color, 26f))
                assertSame(brushes.forChecker(color, 18f), brushes.forChecker(color, 18f))
                CheckerStyle.entries.forEach { style ->
                    listOf(18f, 26f, 23.25f, 27.4f).forEach { radius ->
                        fun render(reference: Boolean): Bitmap {
                            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                                Canvas(bitmap.asImageBitmap()), Size(100f, 100f)) {
                                if (reference) drawReferenceChecker(Offset(48.25f, 46.5f), color,
                                    theme.palette, style, radius, .7f)
                                else drawChecker(Offset(48.25f, 46.5f), color, theme.palette, style,
                                    radius, .7f, brushes)
                            }
                            return bitmap
                        }
                        val old = render(true)
                        val new = render(false)
                        try {
                            val a = IntArray(10000); val b = IntArray(10000)
                            old.getPixels(a, 0, 100, 0, 0, 100, 100)
                            new.getPixels(b, 0, 100, 0, 0, 100, 100)
                            val channels = intArrayOf(0, 8, 16, 24)
                            for (i in a.indices) for (shift in channels)
                                if (abs(((a[i] ushr shift) and 255) - ((b[i] ushr shift) and 255)) > 2)
                                    fail("Pixel changed: ${theme.id} $style radius=$radius index=$i")
                        } finally { old.recycle(); new.recycle() }
                    }
                }
            }
        }
    }

    @Test fun cachedBrushesRemainCorrectAcrossRepeatedCanvasSizeChanges() {
        ThemeRegistry.all.forEach { theme ->
            val brushes = CheckerBrushes(theme.palette)
            listOf(96, 192, 128, 96).forEach { edge ->
                listOf(theme.palette.ivory, theme.palette.dark).forEach { color ->
                    fun render(reference: Boolean): Bitmap {
                        val image = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
                        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                            Canvas(image.asImageBitmap()), Size(edge.toFloat(), edge.toFloat())) {
                            val center = Offset(edge / 2f, edge / 2f)
                            if (reference) drawReferenceChecker(center, Color(color), theme.palette, CheckerStyle.SCULPTED)
                            else drawChecker(center, Color(color), theme.palette, CheckerStyle.SCULPTED, brushes = brushes)
                        }
                        return image
                    }
                    val original = render(true); val cached = render(false)
                    try {
                        val a = IntArray(edge * edge); val b = IntArray(edge * edge)
                        original.getPixels(a, 0, edge, 0, 0, edge, edge)
                        cached.getPixels(b, 0, edge, 0, 0, edge, edge)
                        val channels = intArrayOf(0, 8, 16, 24)
                        for (i in a.indices) for (shift in channels)
                            if (abs(((a[i] ushr shift) and 255) - ((b[i] ushr shift) and 255)) > 2)
                                fail("Stale resized shader: ${theme.id} size=$edge pixel=$i")
                    } finally { original.recycle(); cached.recycle() }
                }
            }
        }
    }
}
