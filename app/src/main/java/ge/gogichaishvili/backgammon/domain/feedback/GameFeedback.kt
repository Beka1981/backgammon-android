package ge.gogichaishvili.backgammon.domain.feedback

import ge.gogichaishvili.backgammon.domain.settings.AppSettings

enum class FeedbackCue { DICE, PICKUP, PLACE, HIT, BEAR_OFF, VICTORY }
enum class HapticCue { TICK, PICKUP, PLACE, IMPACT, CONFIRM }
data class FeedbackDecision(val sound: FeedbackCue?, val haptic: HapticCue?)

/** Pure policy; device/system capabilities are checked again by the platform implementation. */
object FeedbackPolicy {
    fun decide(cue: FeedbackCue, settings: AppSettings, foreground: Boolean, soundAllowed: Boolean): FeedbackDecision {
        if (!foreground) return FeedbackDecision(null, null)
        val sound = if (!settings.soundEffects || !soundAllowed) null
            else if (settings.reducedMotion && cue in listOf(FeedbackCue.DICE, FeedbackCue.VICTORY)) FeedbackCue.PLACE else cue
        val haptic = if (!settings.haptics) null else if (settings.reducedMotion) HapticCue.TICK else when (cue) {
            FeedbackCue.DICE, FeedbackCue.PLACE, FeedbackCue.BEAR_OFF -> HapticCue.PLACE
            FeedbackCue.PICKUP -> HapticCue.PICKUP
            FeedbackCue.HIT -> HapticCue.IMPACT
            FeedbackCue.VICTORY -> HapticCue.CONFIRM
        }
        return FeedbackDecision(sound, haptic)
    }
}

interface GameFeedback {
    fun configure(settings: AppSettings)
    fun play(cue: FeedbackCue, settings: AppSettings)
    fun stop()
    fun close()
}
