package ge.gogichaishvili.backgammon

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import ge.gogichaishvili.backgammon.presentation.game.GameViewModel
import org.junit.*
import org.junit.Assert.*

class GameActivityTest {
    @get:Rule(order = 2) val pageSizeCompatibility = PageSizeCompatibilityRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun activityRecreationAndLandscapeKeepGameAndConfirmationState() {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("game_board").fetchSemanticsNodes().isNotEmpty() }
        lateinit var model: GameViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[GameViewModel::class.java] }
        val game = model.state.value.game
        compose.onNodeWithTag("new_game").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_new_game").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("confirm_new_game").assertIsDisplayed()
        assertEquals(game, model.state.value.game)
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.onNodeWithTag("confirm_new_game").assertIsDisplayed()
        assertEquals(game, model.state.value.game)
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}
