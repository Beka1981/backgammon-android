package ge.gogichaishvili.backgammon

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.presentation.*
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themeSelectionUpdatesBoardAfterNavigatingBack() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent {
            BackgammonApp(
                SettingsUiState(settings, loaded = true), ThemeRegistry.all,
                onTheme = { settings = settings.copy(themeId = it) },
                onUpdate = { settings = it(settings) }, onRetry = {}, onDismissError = {})
        }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings_grid").performScrollToIndex(2)
        compose.onNodeWithTag("theme_royal").performClick().assertIsSelected()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Royal Black & Gold").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Backgammon board. Standard starting position with 15 ivory and 15 dark checkers. " +
                "Illustrative dice show three and five. Gameplay is not enabled."
        ).assertIsDisplayed()
    }

    @Test fun settingsDestinationSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            BackgammonApp(SettingsUiState(loaded = true), ThemeRegistry.all, {}, {}, {}, {})
        }
        compose.onNodeWithText("Settings").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Back").assertIsDisplayed()
        compose.onNodeWithText("Choose your table").assertIsDisplayed()
    }
}
