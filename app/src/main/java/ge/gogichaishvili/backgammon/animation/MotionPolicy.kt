package ge.gogichaishvili.backgammon.animation

import ge.gogichaishvili.backgammon.domain.settings.AnimationSpeed

enum class MotionKind(val baseMillis: Int) {
    CHECKER_MOVE(300), SELECTION(140), DRAG_SETTLE(180), STACK(220),
    DICE_ROLL(650), DICE_BOUNCE(320), HIT_TO_BAR(360), BEAR_OFF(340),
    LEGAL_MOVE(160), TURN(260), VICTORY(900), THEME(280), NAVIGATION(220),
}

/**
 * Finite, interruptible Compose animations consume this policy. No game state or dice
 * outcome is derived from animation. Drag follows pointer directly; settling uses DRAG_SETTLE.
 * Compose also honors the system duration scale. Never start background animation loops.
 */
data class MotionPolicy(
    val speed: AnimationSpeed = AnimationSpeed.NORMAL,
    val reducedMotion: Boolean = false,
) {
    fun duration(kind: MotionKind): Int =
        if (reducedMotion) 0 else (kind.baseMillis * speed.multiplier).toInt()
}
