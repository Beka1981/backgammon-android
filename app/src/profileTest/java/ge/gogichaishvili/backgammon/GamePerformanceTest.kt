package ge.gogichaishvili.backgammon

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.*
import android.view.FrameMetrics
import android.view.Window
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ge.gogichaishvili.backgammon.domain.match.MatchRecord
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.game.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Real Choreographer clock: deliberately no Compose test rule or virtual animation clock.
 * Opt in with -PprofileTests=true; runs against the non-debuggable/R8 profile APK.
 */
class GamePerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var game: GameViewModel
    private var moves = 0

    private fun await(label: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 60000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < until) { "Timed out: " + label }
            SystemClock.sleep(10)
        }
        assertNull(game.state.value.storageIssue)
    }
    private fun command(action: GameUiAction) {
        val revision = game.state.value.revision
        game.dispatch(action)
        await("committed action") { game.state.value.revision != revision && !game.state.value.busy }
    }
    private fun turn() {
        if (game.state.value.game.turn is TurnState.Complete)
            command(GameUiAction.EndTurn(game.state.value.revision))
        if (game.state.value.canRoll) command(GameUiAction.Roll(game.state.value.revision))
        while (game.state.value.game.turn is TurnState.Moving) {
            val move = game.state.value.legalMoves[0]
            val source = when (val from = move.from) {
                MoveSource.Bar -> BoardTarget.Bar
                is MoveSource.OnPoint -> BoardTarget.Point(from.number)
            }
            command(GameUiAction.Tap(source, game.state.value.revision))
            command(GameUiAction.Play(move, game.state.value.revision))
            moves++
        }
    }

    @Test fun realClockGameplayThemeAndOrientationMeasurements() {
        val context = instrumentation.targetContext
        assertTrue("Use the isolated profile package", context.packageName.endsWith(".profile"))
        assertEquals("Profiling must use a non-debuggable APK", 0,
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val app = context.applicationContext as BackgammonApplication
        runBlocking {
            app.container.matches.save(MatchRecord(BackgammonEngine.newGame()))
            app.container.settings.update { AppSettings(soundEffects = false, haptics = false) }
        }
        val frames = Frames(app)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var available = false
            val until = SystemClock.uptimeMillis() + 60000
            while (!available) {
                scenario.onActivity {
                    runCatching { it.viewModelStore["androidx.lifecycle.ViewModelProvider.DefaultKey:" + GameViewModel::class.java.canonicalName] as GameViewModel }.getOrNull()?.let { model ->
                        game = model; available = model.state.value.ready
                    }
                }
                check(SystemClock.uptimeMillis() < until)
                if (!available) SystemClock.sleep(25)
            }
            assertNull(game.state.value.storageIssue)
            // Warm the actual route, dice, rules, storage and checker drawing before measuring.
            turn()
            val output = JSONObject().put("package", context.packageName).put("debuggable", false).put("sdk", Build.VERSION.SDK_INT)
                .put("device", Build.MODEL).put("pid", android.os.Process.myPid()).put("clock", "real Choreographer")
                .put("soundAndHaptics", "disabled for consistent rendering measurement")
            scenario.onActivity { output.put("refreshHz", it.window.decorView.display?.refreshRate) }
            output.put("gameplay", frames.stage("gameplay") {
                val before = moves
                repeat(2) { turn() }
                output.put("measuredMoves", moves - before)
            })
            output.put("theme", frames.stage("theme") {
                listOf("royal", "ocean", "classic").forEach { skin ->
                    runBlocking { app.container.settings.update { it.copy(themeId = skin) } }
                    SystemClock.sleep(500)
                }
            })
            val committed = game.state.value.game
            output.put("orientation", frames.stage("orientation") {
                fun rotate(request: Int, expected: Int) {
                    scenario.onActivity { it.requestedOrientation = request }
                    await("orientation " + expected) {
                        var actual = Configuration.ORIENTATION_UNDEFINED
                        scenario.onActivity { actual = it.resources.configuration.orientation }
                        actual == expected
                    }
                    SystemClock.sleep(1000) // Include settling frames after actual recreation.
                    scenario.onActivity { assertEquals(expected, it.resources.configuration.orientation) }
                }
                rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
                rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
                assertEquals(committed, game.state.value.game)
            })
            val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let { File(it, "task05-performance") } ?: File(context.getExternalFilesDir(null), "task05-performance")
            check(directory.isDirectory || directory.mkdirs())
            java.io.FileOutputStream(File(directory, "performance.json")).use {
                it.write(output.toString(2).toByteArray(Charsets.UTF_8))
            }
            instrumentation.sendStatus(0, Bundle().apply { putString("task05_performance", output.toString()) })
        } finally {
            instrumentation.runOnMainSync { frames.close() }
            scenario.close()
        }
    }

    private class Frames(private val app: Application) : Application.ActivityLifecycleCallbacks {
        private val thread = HandlerThread("FrameMeasurements").apply { start() }
        private val handler = Handler(thread.looper)
        private val windows = mutableSetOf<Window>()
        private val lock = Any()
        private var current: Samples? = null
        private var injected = false
        private var dieIndex = 0
        private val dicePattern = intArrayOf(6, 1, 2, 2, 3, 4)
        private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            synchronized(lock) {
                current?.let {
                    it.total += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                    it.draw += metrics.getMetric(FrameMetrics.DRAW_DURATION)
                    it.layout += metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                    it.dropped += dropped
                }
            }
        }
        init { app.registerActivityLifecycleCallbacks(this) }
        private fun allocated() = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: -1
        private fun gcCount() = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: -1
        fun stage(name: String, block: () -> Unit): JSONObject {
            val samples = Samples()
            val bytes = allocated()
            val gc = gcCount()
            val before = SystemClock.elapsedRealtimeNanos()
            synchronized(lock) { current = samples }
            Trace.beginSection("BG.Stage." + name)
            try { block() }
            finally {
                Trace.endSection()
                synchronized(lock) { current = null }
            }
            val after = SystemClock.elapsedRealtimeNanos()
            val result = synchronized(lock) {
                assertTrue("No measured frames: " + name, samples.total.isNotEmpty())
                fun distribution(values: List<Long>): JSONObject {
                    val sorted = java.util.ArrayList(values)
                    java.util.Collections.sort(sorted)
                    fun percentile(f: Double) = sorted[((sorted.size - 1) * f).toInt()] / 1e6
                    return JSONObject().put("count", sorted.size).put("p50Ms", percentile(.5))
                        .put("p95Ms", percentile(.95)).put("maxMs", percentile(1.0))
                        .put("over16_67Ms", values.count { it > 16666667 })
                }
                JSONObject().put("totalFrame", distribution(samples.total)).put("drawDuration", distribution(samples.draw))
                    .put("layoutDuration", distribution(samples.layout)).put("callbackDrops", samples.dropped)
            }
            return result.put("wallMs", (after - before) / 1e6).put("allocatedBytes", allocated() - bytes)
                .put("gcCount", gcCount() - gc).put("allocationScope", "whole instrumented app process; includes test overhead")
        }
        private class Samples {
            val total = mutableListOf<Long>()
            val draw = mutableListOf<Long>()
            val layout = mutableListOf<Long>()
            var dropped = 0
        }
        override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
            if (activity is MainActivity && !injected) {
                // Test-only constructor injection; identical dice workload for baseline and polished runs.
                val model = GameViewModel(SavedStateHandle(emptyMap()), LocalDice(DieSource {
                    dicePattern[dieIndex++ % dicePattern.size]
                }), matches = (app as BackgammonApplication).container.matches)
                activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:" +
                    GameViewModel::class.java.canonicalName, model)
                injected = true
            }
        }
        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity && windows.add(activity.window))
                activity.window.addOnFrameMetricsAvailableListener(listener, handler)
        }
        override fun onActivityDestroyed(activity: Activity) {
            if (windows.remove(activity.window)) activity.window.removeOnFrameMetricsAvailableListener(listener)
        }
        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        fun close() {
            app.unregisterActivityLifecycleCallbacks(this)
            windows.forEach { it.removeOnFrameMetricsAvailableListener(listener) }
            windows.clear(); thread.quitSafely()
        }
    }
}
