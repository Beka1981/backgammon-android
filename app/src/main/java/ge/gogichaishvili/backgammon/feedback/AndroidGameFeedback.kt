package ge.gogichaishvili.backgammon.feedback

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import ge.gogichaishvili.backgammon.domain.feedback.*
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Activity-owned, short non-looping cues. Respects system sound/haptics without extra permissions. */
class AndroidGameFeedback(context: Context, private val view: View, private val scope: CoroutineScope) : GameFeedback {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val hasOutput = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    private val cache = context.applicationContext.cacheDir
    private val lock = Any()
    private var pool: SoundPool? = null
    private var closed = false
    private var prepare: Job? = null
    private val ids = ConcurrentHashMap<FeedbackCue, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val streams = ArrayDeque<Int>()

    override fun configure(settings: AppSettings) {
        synchronized(lock) {
            if (closed) return
            if (!settings.soundEffects) { stop(); return }
            if (!hasOutput || pool != null) return
            val created = try {
                SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            } catch (_: RuntimeException) { return } // Optional platform facility.
            pool = created
            created.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
            prepare = scope.launch(Dispatchers.IO) {
                FeedbackCue.entries.forEach { cue ->
                    ensureActive()
                    try {
                        val file = File(cache, "backgammon_cue_v1_" + cue.name.lowercase() + ".wav")
                        if (!file.isFile || file.length() < 44) file.writeBytes(ProceduralAudio.wav(cue))
                        synchronized(lock) { if (!closed) ids[cue] = created.load(file.absolutePath, 1) }
                    } catch (_: IOException) {
                        // Cache failure never affects game actions; never replay a delayed cue.
                    } catch (_: RuntimeException) {
                        // Audio output can disappear or become unavailable.
                    }
                }
            }
        }
    }

    override fun play(cue: FeedbackCue, settings: AppSettings) {
        val foreground = view.isAttachedToWindow && view.hasWindowFocus()
        val soundAllowed = try {
            hasOutput && audio != null && audio.mode == AudioManager.MODE_NORMAL &&
                audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                !audio.isStreamMute(AudioManager.STREAM_MUSIC) && audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        } catch (_: RuntimeException) { false }
        val decision = FeedbackPolicy.decide(cue, settings, foreground, soundAllowed)
        synchronized(lock) {
            if (closed) return
            decision.sound?.let { sound ->
                val id = ids[sound]
                val output = pool
                if (output != null && id != null && id in loaded) {
                    val stream = output.play(id, .38f, .38f, 1, 0, 1f)
                    if (stream != 0) streams.addLast(stream)
                    while (streams.size > 16) output.stop(streams.removeFirst())
                }
            }
        }
        decision.haptic?.let {
            val constant = when (it) {
                HapticCue.TICK -> HapticFeedbackConstants.CLOCK_TICK
                HapticCue.PICKUP -> HapticFeedbackConstants.KEYBOARD_TAP
                HapticCue.PLACE -> HapticFeedbackConstants.VIRTUAL_KEY
                HapticCue.IMPACT -> HapticFeedbackConstants.CONTEXT_CLICK
                HapticCue.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.VIRTUAL_KEY
            }
            // No IGNORE flags: system settings and hardware capability determine availability.
            view.performHapticFeedback(constant)
        }
    }

    override fun stop() = synchronized(lock) {
        while (streams.isNotEmpty()) pool?.stop(streams.removeFirst())
        Unit
    }
    override fun close() = synchronized(lock) {
        if (!closed) {
            prepare?.cancel()
            stop()
            closed = true
            pool?.release(); pool = null
            ids.clear(); loaded.clear()
        }
    }
}
