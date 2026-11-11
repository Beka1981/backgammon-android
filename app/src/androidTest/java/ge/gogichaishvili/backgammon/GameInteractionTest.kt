package ge.gogichaishvili.backgammon

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.presentation.game.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class GameInteractionTest {
    @get:Rule(order = 2) val pageSizeCompatibility = PageSizeCompatibilityRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var model: GameViewModel
    @After fun clear() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }

    private fun show(board: BoardState = BoardState.initial(), roll: DiceRoll = DiceRoll(6, 1), opening: Boolean = false,
        reducedMotion: Boolean = true) {
        val values = ArrayDeque(listOf(roll.first, roll.second))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = GameViewModel(SavedStateHandle(), LocalDice(DieSource { values.removeFirst() }),
                initialGame = if (opening) BackgammonEngine.newGame() else BackgammonEngine.fromPosition(board, Player.WHITE))
            store.put("game", model)
            model.dispatch(GameUiAction.SetMotion(MotionPolicy(reducedMotion = reducedMotion)))
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            BackgammonApp(SettingsUiState(AppSettings(), loaded = true), ThemeRegistry.all,
                {}, {}, {}, {}, state, model::dispatch)
        }
        compose.waitUntil(60000) { model.state.value.ready }
        awaitAppWindow()
        compose.waitUntil(60000) {
            runCatching { compose.onNodeWithTag("game_board").assertIsDisplayed(); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("game_board").assertIsDisplayed()
    }

    private fun settleAnimations() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.waitUntil(60000) { !model.state.value.busy }
    }
    private fun roll() {
        compose.onNodeWithTag("roll_dice").performClick()
        compose.waitUntil(60000) { model.state.value.lastRoll != null }
        settleAnimations()
    }
    private fun tap(tag: String) {
        val revision = model.state.value.revision
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(60000) { model.state.value.revision != revision }
        settleAnimations()
    }
    private fun awaitAppWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(60000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() ==
                instrumentation.targetContext.packageName
        }
    }
    private fun screenshot(name: String) {
        awaitAppWindow()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val additionalOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (additionalOutput != null) File(additionalOutput, "task04-screenshots")
            else File(instrumentation.targetContext.getExternalFilesDir(null), "task04-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name + ".png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
    private fun board(white: Map<Int, Int>, black: Map<Int, Int> = mapOf(12 to 15), bar: Int = 0): BoardState {
        val points = MutableList(24) { Point() }
        white.forEach { (p, n) -> points[p - 1] = Point(Player.WHITE, n) }
        black.forEach { (p, n) -> points[p - 1] = Point(Player.BLACK, n) }
        return BoardState(points, CheckerCounts(bar, 0),
            CheckerCounts(15 - white.values.sum() - bar, 15 - black.values.sum()))
    }

    @Test fun realPointerTapsSelectHighlightAndMoveWithActualEngineDice() {
        show(opening = true, reducedMotion = false)
        screenshot("01-initial")
        roll()
        screenshot("02-dice")
        assertEquals(Player.WHITE, model.state.value.game.currentPlayer)
        compose.onNodeWithTag("roll_dice").assertIsNotEnabled()
        compose.onNodeWithTag("point_8").performTouchInput { click(center) }
        compose.waitUntil(60000) { model.state.value.selected == MoveSource.OnPoint(8) }
        compose.onNodeWithTag("point_8").assertIsSelected()
        compose.onNodeWithTag("point_7").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Legal destination"))
        screenshot("03-selected")
        compose.onNodeWithTag("point_7").performTouchInput { click(center) }
        compose.waitUntil(60000) { model.state.value.game.board.point(7).owner == Player.WHITE }
        settleAnimations()
        assertEquals(2, model.state.value.game.board.point(8).count)
        assertNull(model.state.value.selected)
        compose.onNodeWithTag("die_1").assertContentDescriptionEquals("Die 1 · Used")
        screenshot("04-after-move")
    }

    @Test fun barEntryIsAvailableWhileOtherSourcesAreForbidden() {
        show(board(mapOf(8 to 1), bar = 1), DiceRoll(1, 2))
        roll()
        compose.onNodeWithTag("point_8").assertHasNoClickAction()
        tap("bar")
        tap("point_24")
        assertEquals(0, model.state.value.game.board.bar.white)
        assertEquals(Point(Player.WHITE, 1), model.state.value.game.board.point(24))
    }

    @Test fun bearingOffShowsReproducibleGammonDialog() {
        show(board(mapOf(1 to 1)), DiceRoll(1, 2), reducedMotion = false)
        roll(); tap("point_1"); tap("white_tray")
        compose.onNodeWithTag("game_over").assertIsDisplayed()
        compose.onNode(hasText("White wins") and hasAnyAncestor(hasTestTag("game_over"))).assertIsDisplayed()
        compose.onNodeWithText("Gammon · 2 points").assertIsDisplayed()
        assertEquals(15, model.state.value.game.board.borneOff.white)
        screenshot("05-game-over")
    }

    @Test fun ambiguousBearOffOffersExplicitDieChoice() {
        show(board(mapOf(1 to 2)), DiceRoll(1, 2))
        roll(); tap("point_1"); tap("white_tray")
        compose.onNodeWithText("Choose a die").assertIsDisplayed()
        compose.onNodeWithText("Use 2").performClick()
        compose.waitUntil(60000) { model.state.value.game.board.borneOff.white == 14 && !model.state.value.busy }
        assertEquals(listOf(1), (model.state.value.game.turn as TurnState.Moving).remainingDice)
    }

    @Test fun doublesUseFourMovesBeforeTurnCanSwitch() {
        show(board(mapOf(20 to 1), mapOf(1 to 15)), DiceRoll(2, 2))
        roll()
        (0..3).forEach { compose.onNodeWithTag("die_" + it).assertIsDisplayed() }
        listOf(20 to 18, 18 to 16, 16 to 14, 14 to 12).forEach { (from, to) ->
            tap("point_" + from); tap("point_" + to)
        }
        compose.onNodeWithTag("end_turn").assertIsEnabled().performClick()
        compose.waitUntil(60000) { model.state.value.game.currentPlayer == Player.BLACK }
        compose.onNodeWithTag("roll_dice").assertIsEnabled()
    }

    @Test fun newGameDialogCanCancelOrResetWithoutChangingRulesState() {
        show()
        roll()
        val previous = model.state.value.game
        compose.onNodeWithTag("new_game").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(60000) { !model.state.value.confirmNewGame }
        assertEquals(previous, model.state.value.game)
        compose.onNodeWithTag("new_game").performClick()
        compose.onNodeWithTag("confirm_new_game").performClick()
        compose.waitUntil(60000) { model.state.value.game.turn == TurnState.Opening }
        assertEquals(BoardState.initial(), model.state.value.game.board)
    }

    @Test fun hitMotionEndsWithOneCheckerOnTheOpponentsBar() {
        show(board(mapOf(8 to 1), mapOf(5 to 1, 12 to 14)), DiceRoll(3, 1), reducedMotion = false)
        roll(); tap("point_8"); tap("point_5")
        assertEquals(1, model.state.value.game.board.bar.black)
        assertEquals(Point(Player.WHITE, 1), model.state.value.game.board.point(5))
        compose.onNodeWithTag("bar").assertContentDescriptionEquals("Bar · White: 0 · Black: 1")
    }
}
