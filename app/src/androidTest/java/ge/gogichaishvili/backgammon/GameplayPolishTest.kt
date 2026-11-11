package ge.gogichaishvili.backgammon

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.data.*
import ge.gogichaishvili.backgammon.domain.match.*
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.presentation.game.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class GameplayPolishTest {
    @get:Rule(order = 2) val pageSizeCompatibility = PageSizeCompatibilityRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var store = ViewModelStore()
    private var diskJob: Job? = null
    private val file = File(instrumentation.targetContext.cacheDir, "task05-" + UUID.randomUUID() + ".bin")
    private lateinit var repository: MatchRepository
    private lateinit var model: GameViewModel
    private val active = mutableStateOf<GameViewModel?>(null)
    private val skin = mutableStateOf("classic")

    private fun open() {
        val job = SupervisorJob()
        diskJob = job
        repository = DataStoreMatchRepository(DataStoreFactory.create(
            serializer = MatchSerializer, corruptionHandler = ReplaceFileCorruptionHandler { StoredMatch.Recovered },
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }))
    }
    private fun model(reduced: Boolean) {
        instrumentation.runOnMainSync {
            model = GameViewModel(SavedStateHandle(), matches = repository, visualTimeoutMillis = 120000)
            store.put("game", model)
            model.dispatch(GameUiAction.SetMotion(MotionPolicy(reducedMotion = reduced)))
            active.value = model
        }
        compose.waitUntil(60000) { model.state.value.ready }
    }
    private fun show(reduced: Boolean = true) {
        model(reduced)
        compose.setContent {
            val current = active.value!!
            val state by current.state.collectAsStateWithLifecycle()
            BackgammonApp(SettingsUiState(AppSettings(themeId = skin.value), loaded = true),
                ThemeRegistry.all, {}, {}, {}, {}, state, current::dispatch)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("game_board").assertIsDisplayed()
    }
    private fun seededHit() {
        open()
        val points = MutableList(24) { Point() }
        points[7] = Point(Player.WHITE, 1)
        points[4] = Point(Player.BLACK, 1)
        points[11] = Point(Player.BLACK, 14)
        val board = BoardState(points, borneOff = CheckerCounts(14, 0))
        val roll = DiceRoll(3, 1)
        val game = (BackgammonEngine.transition(BackgammonEngine.fromPosition(board, Player.WHITE),
            GameAction.ApplyDice(roll)) as ActionResult.Accepted).state
        runBlocking { repository.save(MatchRecord(game, roll, roll.uses, Player.WHITE)) }
    }
    private fun screenshot(name: String) {
        val title = when (skin.value) {
            "ocean" -> "Modern Ocean"
            "royal" -> "Royal Black & Gold"
            else -> "Classic Luxury"
        }
        compose.waitUntil(60000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        // Start/finish the Crossfade after the new snapshot has actually reached composition.
        compose.mainClock.advanceTimeBy(2000)
        compose.waitUntil(60000) { compose.onAllNodesWithTag("game_board").fetchSemanticsNodes().size == 1 }
        // Check presented pixels, not just the title/semantics of a new Crossfade target.
        val expected = Color(ThemeRegistry.resolve(skin.value).palette.pointLight)
        compose.waitUntil(120000) {
            runCatching {
                val board = compose.onNodeWithTag("game_board").captureToImage()
                val x = (BoardGeometry.pointCenter(13).x / BoardGeometry.width * board.width).toInt()
                val y = (180f / BoardGeometry.height * board.height).toInt()
                val pixel = board.toPixelMap()[x, y]
                abs(pixel.red - expected.red) < .015f &&
                    abs(pixel.green - expected.green) < .015f && abs(pixel.blue - expected.blue) < .015f
            }.getOrDefault(false)
        }
        compose.waitForIdle()
        // System screenshots read the presented surface, beyond Compose's virtual frame clock.
        instrumentation.uiAutomation.waitForIdle(250, 10000)
        android.os.SystemClock.sleep(500)
        compose.waitUntil(60000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() ==
            instrumentation.targetContext.packageName }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "task05-screenshots")
            else File(instrumentation.targetContext.getExternalFilesDir(null), "task05-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name + ".png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @After fun close() {
        compose.mainClock.autoAdvance = true
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { diskJob?.cancelAndJoin() }
        file.delete()
    }

    @Test fun fileReopenRestoresAnExactMidTurnWithoutReplayingAnimations() {
        seededHit(); show()
        compose.onNodeWithTag("point_8").performClick()
        compose.waitUntil(60000) { model.state.value.selected != null }
        compose.onNodeWithTag("point_5").performClick()
        compose.waitUntil(60000) { model.state.value.game.board.bar.black == 1 && !model.state.value.busy }
        val saved = model.state.value
        instrumentation.runOnMainSync { store.clear(); store = ViewModelStore() }
        runBlocking { diskJob!!.cancelAndJoin() }
        open(); model(reduced = true)
        compose.waitForIdle()
        assertEquals(saved.game, model.state.value.game)
        assertEquals(saved.dice, model.state.value.dice)
        assertFalse(model.state.value.busy)
        compose.onNodeWithTag("bar").assertContentDescriptionEquals("Bar · White: 0 · Black: 1")
    }

    @Test fun corruptedFileRecoversWithVisibleNoticeAndDurablySavesFreshGame() {
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        open(); show()
        assertEquals(GameNotice.RESTORE_FAILED, model.state.value.notice)
        compose.onNodeWithTag("game_notice").assertExists()
        assertEquals(BackgammonEngine.newGame(), model.state.value.game)
        assertEquals(model.state.value.game, runBlocking { repository.load().record!!.game })
    }

    @Test fun themeChangeDuringHitKeepsOneAnimationOwnerAndInterruptionKeepsCommittedBoard() {
        seededHit(); show(reduced = false)
        compose.onNodeWithTag("point_8").performClick()
        compose.waitUntil(60000) { model.state.value.selected != null }
        screenshot("01-selected-classic")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("point_5").performClick()
        compose.waitUntil(60000) { model.state.value.checkerTransition != null }
        compose.mainClock.advanceTimeBy(64)
        val committed = model.state.value.game
        val effect = requireNotNull(model.state.value.checkerTransition)
        compose.runOnIdle { skin.value = "ocean" }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertEquals(effect.id, model.state.value.checkerTransition?.id)
        repeat(10) { model.dispatch(GameUiAction.Play(effect.move, effect.id)) }
        model.dispatch(GameUiAction.CancelAnimations)
        compose.waitUntil(60000) { !model.state.value.busy }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(60000) {
            runCatching { compose.onNodeWithTag("point_5").assertHasClickAction(); true }.getOrDefault(false)
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        assertEquals(committed, model.state.value.game)
        assertEquals(committed, runBlocking { repository.load().record!!.game })
        assertEquals(1, committed.board.bar.black)
        screenshot("02-hit-ocean")
        compose.runOnIdle { skin.value = "royal" }
        compose.mainClock.advanceTimeBy(1000)
        screenshot("03-hit-royal")
        assertEquals(committed, model.state.value.game)
    }
}
