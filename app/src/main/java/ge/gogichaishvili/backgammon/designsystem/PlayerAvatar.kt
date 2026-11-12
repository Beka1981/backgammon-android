package ge.gogichaishvili.backgammon.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ge.gogichaishvili.backgammon.domain.profile.BuiltInAvatars
import kotlin.math.*

/** Original procedural medallions, no downloaded artwork or external URLs. Stable catalogue IDs. */
@Composable
fun PlayerAvatar(id: String, description: String, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val unit = size.minDimension
        val c = center
        val ink = palette.primary
        drawCircle(Brush.radialGradient(listOf(palette.primaryContainer, palette.surface), c, unit * .55f))
        drawCircle(ink.copy(alpha = .25f), unit * .46f, style = Stroke(unit * .018f))
        drawCircle(ink.copy(alpha = .65f), unit * .42f, style = Stroke(unit * .006f))
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun polygon(vararg points: Pair<Float, Float>) {
            val path = Path()
            points.forEachIndexed { index, p -> if (index == 0) path.moveTo(p.first * unit, p.second * unit)
                else path.lineTo(p.first * unit, p.second * unit) }
            path.close(); drawPath(path, ink)
        }
        fun line(a: Offset, b: Offset) = drawLine(ink, a, b, unit * .024f)
        when (BuiltInAvatars.safe(id)) {
            "crown" -> {
                polygon(.25f to .35f, .38f to .46f, .5f to .27f, .62f to .46f,
                    .75f to .35f, .68f to .65f, .32f to .65f)
                drawRoundRect(ink, point(.32f,.69f), Size(unit*.36f,unit*.045f))
            }
            "knight" -> {
                polygon(.30f to .70f,.39f to .50f,.30f to .43f,.44f to .28f,.60f to .28f,
                    .66f to .19f,.70f to .44f,.63f to .69f)
                drawCircle(palette.surface,unit*.025f, point(.49f,.37f))
                line(point(.28f,.75f),point(.72f,.75f))
            }
            "sun" -> {
                drawCircle(ink,unit*.16f)
                repeat(12) { val a = it * PI/6
                    line(c + Offset(cos(a).toFloat(),sin(a).toFloat())*unit*.22f,
                        c + Offset(cos(a).toFloat(),sin(a).toFloat())*unit*.31f) }
            }
            "moon" -> {
                drawCircle(ink,unit*.25f)
                drawCircle(palette.primaryContainer,unit*.22f,point(.61f,.41f))
                polygon(.68f to .59f,.70f to .65f,.77f to .67f,.70f to .69f,.68f to .75f,
                    .66f to .69f,.60f to .67f,.66f to .65f)
            }
            "mountain" -> {
                polygon(.19f to .70f,.43f to .29f,.55f to .48f,.67f to .35f,.82f to .70f)
                val cap = Path().apply { moveTo(unit*.36f,unit*.41f);lineTo(unit*.43f,unit*.29f)
                    lineTo(unit*.50f,unit*.40f);close() }
                drawPath(cap,palette.surface)
            }
            "lotus" -> {
                repeat(5) { i ->
                    val a = (i*36-162)*PI/180
                    val end = c + Offset(cos(a).toFloat(),sin(a).toFloat())*unit*.29f
                    val p = Path().apply {
                        moveTo(unit*.5f,unit*.72f)
                        quadraticTo(end.x-unit*.11f,end.y,end.x,end.y)
                        quadraticTo(end.x+unit*.11f,end.y,unit*.5f,unit*.72f)
                    }
                    drawPath(p,ink, style=Stroke(unit*.022f))
                }
            }
            "shield" -> {
                polygon(.28f to .29f,.72f to .29f,.69f to .59f,.5f to .77f,.31f to .59f)
                drawLine(palette.surface,point(.5f,.35f),point(.5f,.65f),unit*.02f)
                drawLine(palette.surface,point(.37f,.48f),point(.63f,.48f),unit*.02f)
            }
            "star" -> {
                val p = Path()
                repeat(10) { i ->
                    val angle = (i*36-90)*PI/180
                    val radius = if (i%2==0) .29f else .13f
                    val x=c.x+cos(angle).toFloat()*unit*radius
                    val y=c.y+sin(angle).toFloat()*unit*radius
                    if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
                }
                p.close();drawPath(p,ink)
            }
        }
    }
}
