package ge.gogichaishvili.backgammon

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.data.*
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
import kotlin.math.abs

/** Real board pointer input + engine + disk reopen + presented checker pixels, not semantics alone. */
class CheckerMovementRegressionTest {
    @get:Rule(order = 2) val pageSizeCompatibility = PageSizeCompatibilityRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var store = ViewModelStore()
    private var diskJob: Job? = null
    private val file = File(instrumentation.targetContext.cacheDir, "movement-" + UUID.randomUUID() + ".bin")
    private lateinit var repository: DataStoreMatchRepository
    private lateinit var model: GameViewModel
    private val active = mutableStateOf<GameViewModel?>(null)
    private lateinit var skin: String
    private val evidence = StringBuilder()

    private fun open() {
        val job = SupervisorJob()
        diskJob = job
        repository = DataStoreMatchRepository(DataStoreFactory.create(
            serializer = MatchSerializer, scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }))
    }
    private fun newModel() {
        val dice = ArrayDeque(listOf(6, 1))
        instrumentation.runOnMainSync {
            model = GameViewModel(SavedStateHandle(), LocalDice(DieSource { dice.removeFirst() }),
                matches = repository, visualTimeoutMillis = 120000)
            store.put("game", model)
            model.dispatch(GameUiAction.SetMotion(MotionPolicy(reducedMotion = false)))
            active.value = model
        }
        compose.waitUntil(60000) { model.state.value.ready }
    }
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.waitUntil(60000) { !model.state.value.busy }
        compose.onAllNodesWithTag("game_board").assertCountEquals(1)
    }
    private fun frame(): ImageBitmap {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("game_board").fetchSemanticsNode().boundsInWindow
        val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            return Bitmap.createBitmap(screen, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt(), bounds.height.toInt()).asImageBitmap()
        } finally { screen.recycle() }
    }
    private fun pixel(image: ImageBitmap, c: BoardCoordinate): Color =
        image.toPixelMap()[(c.x / BoardGeometry.width * image.width).toInt(),
            (c.y / BoardGeometry.height * image.height).toInt()]
    private fun difference(a: Color, b: Color) =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
    private fun tap(c: BoardCoordinate) {
        compose.onNodeWithTag("game_board").performTouchInput {
            click(Offset(c.x / BoardGeometry.width * width, c.y / BoardGeometry.height * height))
        }
    }
    private fun directory(): File {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        return File(output?.let(::File) ?: instrumentation.targetContext.getExternalFilesDir(null),
            "movement-regression/" + skin).apply { check(isDirectory || mkdirs()) }
    }
    private fun save(name: String, image: ImageBitmap = frame()) {
        File(directory(), name + "-board.png").outputStream().use {
            check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        instrumentation.uiAutomation.waitForIdle(250, 10000)
        val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory(), name + "-screen.png").outputStream().use {
                check(screen.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screen.recycle() }
    }
    private fun record(label: String) {
        val state = model.state.value
        val text = label + " revision=" + state.revision + " selected=" + state.selected +
            " busy=" + state.busy + " moves=" + state.selectedMoves +
            " turn=" + state.game.turn + " board=" + state.game.board
        Log.i("MoveRegression", text)
        evidence.append(text).append("\n")
        File(directory(), "transitions.txt").writeText(evidence.toString())
    }
    private fun move(from: Int, to: Int, expectedCount: Int, label: String) {
        val beforeState = model.state.value.game
        val sourceCount = beforeState.board.point(from).count
        val source = BoardGeometry.checkerCenter(from, sourceCount - 1, sourceCount)
        val destination = BoardGeometry.checkerCenter(to, expectedCount - 1, expectedCount)
        val before = frame()
        save(label + "-before", before)
        val checkerColor = pixel(before, source)
        tap(source)
        compose.waitUntil(60000) { model.state.value.selected == MoveSource.OnPoint(from) }
        compose.onNodeWithTag("point_" + from).assertIsSelected()
        compose.onNodeWithTag("point_" + to).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Legal destination"))
        settle()
        val point = BoardGeometry.pointCenter(to)
        val marker = BoardCoordinate(point.x, if (to >= 13) 286f else 414f)
        record(label + "-selected")
        var selected = frame()
        save(label + "-selected", selected)
        // Surface presentation can lag the Compose test clock; never force a redraw to pass.
        compose.waitUntil(30000) {
            selected = frame()
            // Dual-tone marker: either ring or fill contrasts with each material.
            // A single center/dark-edge pixel is too similar to some skin backgrounds.
            listOf(0f, 18f, -18f, 20f, -20f).maxOf { offset ->
                val probe = BoardCoordinate(marker.x + offset, marker.y)
                difference(pixel(before, probe), pixel(selected, probe))
            } > .08f
        }
        save(label + "-selected-presented", selected)
        val selectionRing = BoardCoordinate(source.x + 31f, source.y)
        assertTrue("Selected checker outline is not visible",
            difference(pixel(before, selectionRing), pixel(selected, selectionRing)) > .08f)
        tap(marker)
        compose.waitUntil(60000) { model.state.value.game.board.point(to).count == expectedCount }
        record(label + "-committed")
        assertNotEquals(beforeState, model.state.value.game)
        settle()
        assertNull(model.state.value.selected)
        assertNull(model.state.value.checkerTransition)
        assertEquals(sourceCount - 1, model.state.value.game.board.point(from).count)
        assertEquals(model.state.value.game, runBlocking { repository.load().record!!.game })
        val after = frame()
        assertTrue("Destination still renders its old pixels",
            difference(checkerColor, pixel(after, destination)) < .04f)
        assertTrue("Source checker was not removed visually",
            difference(checkerColor, pixel(after, source)) > .08f)
        assertTrue("Destination pixels did not change",
            difference(pixel(before, destination), pixel(after, destination)) > .08f)
        record(label + "-settled")
        save(label + "-after", after)
    }
    private fun exercise(id: String) {
        skin = id
        open()
        newModel()
        compose.setContent {
            val current = requireNotNull(active.value)
            val state by current.state.collectAsStateWithLifecycle()
            BackgammonApp(SettingsUiState(AppSettings(themeId = skin), loaded = true),
                ThemeRegistry.all, {}, {}, {}, {}, state, current::dispatch)
        }
        settle()
        compose.onNodeWithTag("roll_dice").performScrollTo().performTouchInput { click(center) }
        compose.waitUntil(60000) { model.state.value.lastRoll == DiceRoll(6, 1) }
        settle()
        assertEquals(Player.WHITE, model.state.value.game.currentPlayer)
        move(8, 7, 1, "01")
        val committed = model.state.value.game
        instrumentation.runOnMainSync { store.clear(); store = ViewModelStore() }
        runBlocking { diskJob!!.cancelAndJoin() }
        open()
        newModel()
        settle()
        assertEquals(committed, model.state.value.game)
        assertEquals(listOf(6), (model.state.value.game.turn as TurnState.Moving).remainingDice)
        assertFalse(model.state.value.busy)
        record("restored")
        save("02-restored")
        move(13, 7, 2, "03-restored-move")
    }
    @After fun close() {
        compose.mainClock.autoAdvance = true
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { diskJob?.cancelAndJoin() }
        file.delete()
    }
    @Test fun classicMovesVisiblyAndAgainAfterDiskRestore() = exercise("classic")
    @Test fun royalMovesVisiblyAndAgainAfterDiskRestore() = exercise("royal")
    @Test fun oceanMovesVisiblyAndAgainAfterDiskRestore() = exercise("ocean")
}
