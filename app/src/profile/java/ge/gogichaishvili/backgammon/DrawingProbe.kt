package ge.gogichaishvili.backgammon

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.domain.settings.CheckerStyle
import ge.gogichaishvili.backgammon.domain.theme.*
import org.json.JSONArray
import org.json.JSONObject

/** Measurement-only code in the target APK: R8 sees the complete drawing call graph.
 * No Compose/library classes cross the target/instrumentation class-loader boundary.
 */
object DrawingProbe {
    @JvmStatic fun measure(context: Context): String {
        val palette = ThemeRegistry.classic.palette
        val brushes = CheckerBrushes(palette)
        val color = Color(palette.ivory)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap.asImageBitmap())
        fun draw(cached: Boolean, count: Int) {
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(100f, 100f)) {
                repeat(count) {
                    if (cached) drawChecker(Offset(48f, 46f), color, palette, CheckerStyle.SCULPTED, brushes = brushes)
                    else drawReferenceChecker(Offset(48f, 46f), color, palette, CheckerStyle.SCULPTED)
                }
            }
        }
        fun bytes() = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
        val samples = JSONArray()
        try {
            draw(false, 100); draw(true, 100)
            // Alternate order to expose warm-up/order effects; whole-process allocation deltas.
            repeat(3) { round ->
                val order = if (round % 2 == 0) listOf(false, true) else listOf(true, false)
                order.forEach { cached ->
                    val allocated = bytes()
                    val cpu = Debug.threadCpuTimeNanos()
                    val wall = SystemClock.elapsedRealtimeNanos()
                    draw(cached, 2000)
                    val wallEnd = SystemClock.elapsedRealtimeNanos()
                    val cpuEnd = Debug.threadCpuTimeNanos()
                    val allocatedEnd = bytes()
                    samples.put(JSONObject().put("cached", cached).put("draws", 2000)
                        .put("allocatedBytes", allocatedEnd - allocated)
                        .put("threadCpuMs", (cpuEnd - cpu) / 1e6).put("wallMs", (wallEnd - wall) / 1e6))
                }
            }
        } finally { bitmap.recycle() }
        val output = JSONObject().put("package", context.packageName)
            .put("debuggable", context.applicationInfo.flags and 2 != 0)
            .put("scope", "paired software Canvas; allocations cover whole instrumented process")
            .put("samples", samples)
        return output.toString(2)
    }
}

private fun DrawScope.drawReferenceChecker(center: Offset, color: Color, p: BoardPalette, style: CheckerStyle,
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

