package ge.gogichaishvili.backgammon.animation

import kotlin.math.abs
import kotlin.math.sin

/** Pure visual curves. Endpoints always match committed board geometry; no game outcomes. */
object GameMotion {
    fun flight(progress: Float, hit: Boolean, hitsOpponent: Boolean): Float = when {
        hit -> ((progress - .45f) / .55f).coerceIn(0f, 1f)
        hitsOpponent -> (progress / .62f).coerceIn(0f, 1f)
        else -> progress.coerceIn(0f, 1f)
    }
    fun lift(progress: Float): Float =
        if (progress <= 0f || progress >= 1f) 0f else sin(progress * Math.PI).toFloat()
    fun diceBounce(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return if (t == 0f || t == 1f) 0f else abs(sin(t * Math.PI * 3)).toFloat() * (1f - t)
    }
    fun diceRotation(progress: Float, index: Int): Float =
        (1f - progress.coerceIn(0f, 1f)) * if (index % 2 == 0) 720f else -1080f
}
