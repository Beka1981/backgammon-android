package ge.gogichaishvili.backgammon.feedback

import ge.gogichaishvili.backgammon.domain.feedback.FeedbackCue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Original synthesized cues. No recordings, third-party samples, or game RNG are used.
 * WAV/PCM is generated off Main and cached only for SoundPool decoding.
 */
internal object ProceduralAudio {
    const val SAMPLE_RATE = 22050
    fun wav(cue: FeedbackCue): ByteArray {
        val duration = when (cue) {
            FeedbackCue.DICE -> .44
            FeedbackCue.PICKUP -> .055
            FeedbackCue.PLACE -> .09
            FeedbackCue.HIT -> .12
            FeedbackCue.BEAR_OFF -> .14
            FeedbackCue.VICTORY -> .45
        }
        val count = (duration * SAMPLE_RATE).toInt()
        val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        fun ascii(s: String) { bytes.put(s.toByteArray(Charsets.US_ASCII)) }
        ascii("RIFF"); bytes.putInt(36 + count * 2); ascii("WAVE")
        ascii("fmt "); bytes.putInt(16); bytes.putShort(1); bytes.putShort(1)
        bytes.putInt(SAMPLE_RATE); bytes.putInt(SAMPLE_RATE * 2); bytes.putShort(2); bytes.putShort(16)
        ascii("data"); bytes.putInt(count * 2)
        var seed = 0x42A91
        fun tone(t: Double, f: Double, decay: Double) =
            if (t < 0) 0.0 else sin(2 * PI * f * t) * exp(-t * decay)
        val dicePulses = doubleArrayOf(0.0, .065, .145, .255, .365)
        repeat(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            seed = seed * 1664525 + 1013904223
            val noise = ((seed ushr 8) / 8388607.5 - 1)
            val sample = when (cue) {
                FeedbackCue.DICE -> dicePulses.sumOf { start ->
                    val dt = t - start
                    if (dt < 0 || dt > .055) 0.0 else
                        (noise * .3 + sin(dt * 2 * PI * 340) * .5) * exp(-dt * 80)
                }
                FeedbackCue.PICKUP -> tone(t, 720.0, 65.0) * .6
                FeedbackCue.PLACE -> tone(t, 185.0, 48.0) * .8 + tone(t, 370.0, 65.0) * .15
                FeedbackCue.HIT -> tone(t, 105.0, 35.0) * .65 + noise * exp(-t * 75) * .3
                FeedbackCue.BEAR_OFF -> tone(t, 520.0, 28.0) * .4 + tone(t, 780.0, 35.0) * .25
                FeedbackCue.VICTORY -> tone(t, 660.0, 18.0) * .35 +
                    tone(t - .10, 880.0, 18.0) * .35 + tone(t - .20, 1100.0, 18.0) * .35
            }
            val attack = minOf(1.0, t / .002)
            val release = minOf(1.0, (duration - t) / .005)
            bytes.putShort((sample * attack * release * 18000).toInt().coerceIn(-32768, 32767).toShort())
        }
        return bytes.array()
    }
}
