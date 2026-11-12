package ge.gogichaishvili.backgammon

import android.graphics.Bitmap
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.presentation.game.*
import org.junit.*
import org.junit.Assert.*

class GameplayAccessibilityTest {
    @get:Rule(order = 2) val pageSizeCompatibility = PageSizeCompatibilityRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var game: GameViewModel
    private val preferences = mutableStateOf(AppSettings(reducedMotion = true))

    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }
    private fun show(language: AppLanguage = AppLanguage.ENGLISH, fontScale: Float = 1f,
        board: BoardState = BoardState.initial()) {
        preferences.value = AppSettings(language = language, reducedMotion = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            game = GameViewModel(SavedStateHandle(), LocalDice(DieSource { 1 }),
                initialGame = BackgammonEngine.fromPosition(board, Player.WHITE))
            store.put("game", game)
            game.dispatch(GameUiAction.SetMotion(MotionPolicy(reducedMotion = true)))
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                val state by game.state.collectAsStateWithLifecycle()
                BackgammonApp(SettingsUiState(preferences.value, loaded = true), ThemeRegistry.all,
                    { id -> preferences.value = preferences.value.copy(themeId = id) },
                    { change -> preferences.value = change(preferences.value) }, {}, {}, state, game::dispatch)
            }
        }
        compose.waitUntil(60000) { game.state.value.ready }
        compose.onNodeWithTag("game_board").assertExists()
    }
    private fun roll() {
        compose.onNodeWithTag("roll_dice").performScrollTo().performClick()
        compose.waitUntil(60000) { game.state.value.lastRoll != null && !game.state.value.busy }
    }
    private fun target(tag: String): SemanticsNodeInteraction =
        compose.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(250, 10000)
        android.os.SystemClock.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val parent = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it, "task06-screenshots") }
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "task06-screenshots")
        check(parent.isDirectory || parent.mkdirs())
        try {
            java.io.FileOutputStream(File(parent, name + ".png")).use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { image.recycle() }
    }
    private fun assertAllFourDiceAndLabelsFit() {
        val display = compose.onNodeWithTag("dice_display").performScrollTo().fetchSemanticsNode()
        val left = display.positionInRoot.x
        val right = left + display.size.width
        fun within(node: SemanticsNode) {
            assertTrue("Die/label starts outside display", node.positionInRoot.x >= left - 1f)
            assertTrue("Die/label overflows display", node.positionInRoot.x + node.size.width <= right + 1f)
        }
        repeat(4) { index ->
            val die = compose.onNodeWithTag("die_$index").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            within(die.fetchSemanticsNode())
        }
        val descendants = compose.onAllNodes(hasAnyAncestor(hasTestTag("dice_display")),
            useUnmergedTree = true).fetchSemanticsNodes()
        descendants.filter { it.config.contains(SemanticsProperties.Text) }.forEach(::within)
    }
    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test fun semanticEquivalentControlsSelectAndMoveWithoutTouchingNarrowPoints() {
        show(); roll()
        target("source_8").assertContentDescriptionEquals("Select checker · Point 8").performClick()
        compose.waitUntil(60000) { game.state.value.selected == MoveSource.OnPoint(8) }
        compose.onNodeWithTag("point_8").assertIsSelected().assert(role(Role.Button))
        assertEquals("Select checker", compose.onNodeWithTag("point_8")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        target("destination_7").assertContentDescriptionEquals("Move checker · Point 7").performClick()
        compose.waitUntil(60000) { game.state.value.game.board.point(7).owner == Player.WHITE && !game.state.value.busy }
        assertNull(game.state.value.selected)
        compose.onNodeWithTag("die_0").assert(role(Role.Image))
    }

    @Test fun georgianAtTwoHundredPercentSupportsGameplayAndConfirmation() {
        show(AppLanguage.GEORGIAN, 2f); roll()
        target("source_8").assertContentDescriptionEquals("ქვის არჩევა · პუნქტი 8").performClick()
        compose.waitUntil(60000) { game.state.value.selected != null }
        target("destination_7").assertContentDescriptionEquals("ქვის გადატანა · პუნქტი 7").performClick()
        compose.waitUntil(60000) { !game.state.value.busy && game.state.value.game.board.point(7).count == 1 }
        val committed = game.state.value.game
        target("new_game").performClick()
        compose.onNodeWithText("დაიწყოს ახალი თამაში?").assertIsDisplayed()
        screenshot("01-georgian-large-dialog")
        compose.onNodeWithText("გაუქმება").performClick()
        compose.waitUntil(60000) { !game.state.value.confirmNewGame }
        assertEquals(committed, game.state.value.game)
    }

    @Test fun settingsExposeRadioAndSwitchStateAndSwitchLanguageAtLargeText() {
        show(AppLanguage.GEORGIAN, 2f)
        compose.onNodeWithText("პარამეტრები").performClick()
        compose.onNodeWithTag("settings_grid").performScrollToNode(hasTestTag("theme_ocean"))
        compose.onNodeWithTag("theme_ocean").assert(role(Role.RadioButton))
            .assertContentDescriptionContains("თანამედროვე ოკეანე").performClick()
        compose.waitUntil(60000) { preferences.value.themeId == "ocean" }
        compose.onNodeWithTag("theme_ocean").assertIsSelected()
        val haptics = hasText("ვიბრაცია") and role(Role.Switch)
        compose.onNodeWithTag("settings_grid").performScrollToNode(haptics)
        compose.onNode(haptics).assertHeightIsAtLeast(48.dp).assertIsOn().performClick()
        compose.onNode(haptics).assertIsOff()
        screenshot("03-georgian-large-settings")
        compose.onNodeWithTag("settings_grid").performScrollToNode(hasText("English") and role(Role.RadioButton))
        compose.onNode(hasText("English") and role(Role.RadioButton)).performClick()
        compose.waitUntil(60000) { preferences.value.language == AppLanguage.ENGLISH }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Modern Ocean").assertExists()
    }

    @Test fun accessibleBearOffOpensReadableVictoryDialogAtLargeText() {
        val points = MutableList(24) { Point() }
        points[0] = Point(Player.WHITE, 1)
        points[11] = Point(Player.BLACK, 15)
        show(fontScale = 2f, board = BoardState(points, borneOff = CheckerCounts(14, 0)))
        roll()
        target("source_1").performClick()
        compose.waitUntil(60000) { game.state.value.selected != null }
        target("destination_off").assertContentDescriptionEquals("Move checker · Bear off").performClick()
        compose.waitUntil(60000) { game.state.value.game.result != null && !game.state.value.busy }
        compose.onNodeWithTag("game_over").assertIsDisplayed()
        compose.onNode(hasText("White wins") and hasAnyAncestor(hasTestTag("game_over"))).assertIsDisplayed()
        compose.onNodeWithText("Gammon · 2 points").assertIsDisplayed()
        screenshot("02-large-victory")
        compose.onNodeWithText("View board").performClick()
        compose.waitUntil(60000) { game.state.value.resultDismissed }
        compose.onNodeWithTag("game_notice").assertTextEquals("Game complete. Start a new game to play again.")
        assertAllFourDiceAndLabelsFit()
        screenshot("04-large-dice")
        val finished = game.state.value.game
        compose.runOnIdle { preferences.value = preferences.value.copy(language = AppLanguage.GEORGIAN) }
        compose.waitForIdle()
        compose.onNodeWithTag("game_notice").assertTextEquals("თამაში დასრულდა. კვლავ სათამაშოდ დაიწყეთ ახალი თამაში.")
        assertAllFourDiceAndLabelsFit()
        screenshot("05-georgian-large-dice")
        assertEquals(finished, game.state.value.game)
    }
}
