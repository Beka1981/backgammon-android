package ge.gogichaishvili.backgammon.feedback

import ge.gogichaishvili.backgammon.domain.feedback.*
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FeedbackTest {
    @Test fun userSoundMuteAndSystemMuteAreIndependentGates() {
        FeedbackCue.entries.forEach { cue ->
            assertNull(FeedbackPolicy.decide(cue, AppSettings(soundEffects = false), true, true).sound)
            assertNull(FeedbackPolicy.decide(cue, AppSettings(), true, false).sound)
            assertEquals(cue, FeedbackPolicy.decide(cue, AppSettings(), true, true).sound)
        }
    }
    @Test fun backgroundNeverMakesSoundOrHaptics() {
        FeedbackCue.entries.forEach {
            assertEquals(FeedbackDecision(null, null), FeedbackPolicy.decide(it, AppSettings(), false, true))
        }
    }
    @Test fun hapticsCanBeDisabledWithoutDisablingSound() {
        FeedbackCue.entries.forEach {
            val decision = FeedbackPolicy.decide(it, AppSettings(haptics = false), true, true)
            assertNull(decision.haptic); assertEquals(it, decision.sound)
        }
    }
    @Test fun reducedMotionUsesOneGentleTapAndShortSound() {
        FeedbackCue.entries.forEach {
            val decision = FeedbackPolicy.decide(it, AppSettings(reducedMotion = true), true, true)
            assertEquals(HapticCue.TICK, decision.haptic)
            assertEquals(if (it == FeedbackCue.DICE || it == FeedbackCue.VICTORY) FeedbackCue.PLACE else it, decision.sound)
        }
    }
    @Test fun generatedAudioHasValidBoundedPcmHeaderAndAudibleSamples() {
        FeedbackCue.entries.forEach {
            val wav = ProceduralAudio.wav(it)
            val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
            assertEquals(1, b.getShort(20).toInt())
            assertEquals(1, b.getShort(22).toInt())
            assertEquals(ProceduralAudio.SAMPLE_RATE, b.getInt(24))
            assertEquals(wav.size - 44, b.getInt(40))
            assertTrue(wav.size < 44144)
            assertTrue((44 until wav.size step 2).any { p -> b.getShort(p) != 0.toShort() })
        }
    }
    @Test fun soundSynthesisIsDeterministicAndCuesAreDistinct() {
        val cues = FeedbackCue.entries.map { ProceduralAudio.wav(it) }
        FeedbackCue.entries.forEachIndexed { i, cue -> assertArrayEquals(cues[i], ProceduralAudio.wav(cue)) }
        assertEquals(cues.size, cues.map { it.contentHashCode() }.distinct().size)
    }
}
