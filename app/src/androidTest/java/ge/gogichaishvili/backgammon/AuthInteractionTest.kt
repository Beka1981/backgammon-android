package ge.gogichaishvili.backgammon

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import ge.gogichaishvili.backgammon.data.auth.EncryptedAuthStorage
import ge.gogichaishvili.backgammon.data.auth.AuthStorageException
import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.domain.settings.AppLanguage
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.presentation.auth.*
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class AuthInteractionTest {
    @get:Rule(order = 2) val compatibility = PageSizeCompatibilityRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { compose.runOnIdle { store.clear() } }
    private class Fake : AuthRepository {
        override val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var calls = 0
        override suspend fun login(email: String, password: String): AuthResult {
            calls++; state.value = AuthState.SignedIn(AuthUser("server-fixture", email)); return AuthResult.Success
        }
        override suspend fun register(email: String, password: String): AuthResult { calls++; return AuthResult.ConfirmationSent }
        override suspend fun logout(): AuthResult { state.value = AuthState.SignedOut; return AuthResult.Success }
        override suspend fun refresh() = AuthResult.Success
        override suspend fun requestPasswordReset(email: String) = AuthResult.ConfirmationSent
        override suspend fun updatePassword(password: String) = AuthResult.Success
        override suspend fun handleCallback(url: String) = AuthResult.Failure(AuthIssue.INVALID_LINK)
    }
    private fun screen(page: AuthPage = AuthPage.WELCOME, repo: Fake = Fake(), local: () -> Unit = {}): Fake {
        lateinit var model: AuthViewModel
        compose.runOnUiThread { model = AuthViewModel(repo); store.put("auth", model); model.dispatch(AuthAction.Page(page)) }
        compose.setContent {
            val ui by model.state.collectAsState()
            LocalizedContent(AppLanguage.ENGLISH) {
                BackgammonTheme(ThemeRegistry.classic) { AuthScreen(ui, model::dispatch, local) }
            }
        }
        return repo
    }
    @Test fun loginRegistrationAndForgotNavigation() {
        screen()
        compose.onNodeWithTag("auth_open_login").performClick()
        compose.onNodeWithTag("auth_to_register").performScrollTo().performClick()
        compose.onNodeWithTag("auth_confirmation").assertExists()
        compose.onNodeWithTag("auth_back_login").performScrollTo().performClick()
        compose.onNodeWithTag("auth_forgot").performScrollTo().performClick()
        compose.onNodeWithTag("auth_email").assertExists()
        compose.onNodeWithTag("auth_password").assertDoesNotExist()
    }
    @Test fun invalidFormNeverCallsRepositoryAndAnnouncesError() {
        val repo = screen(AuthPage.LOGIN)
        compose.onNodeWithTag("auth_email").performTextInput("invalid")
        compose.onNodeWithTag("auth_password").performTextInput("fixture")
        compose.onNodeWithTag("auth_password").performImeAction()
        compose.onNodeWithTag("auth_error").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, repo.calls) }
    }
    @Test fun passwordToggleHasAccessibleLabelAndMinimumTarget() {
        screen(AuthPage.LOGIN)
        compose.onNodeWithTag("auth_password").performTextInput("Fixture password")
        fun renderedPassword(): String {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag("auth_password").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.text.text
        }
        compose.onNodeWithTag("auth_password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertNotEquals("Fixture password", renderedPassword())
        compose.onNodeWithTag("auth_password_visibility").performScrollTo()
            .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        assertEquals("Fixture password", renderedPassword())
        compose.onNodeWithTag("auth_password_visibility").performClick()
        compose.onNodeWithContentDescription("Show password").assertExists()
        assertNotEquals("Fixture password", renderedPassword())
    }
    @Test fun successfulLoginAndLogoutClearAccountScreen() {
        screen(AuthPage.LOGIN)
        compose.onNodeWithTag("auth_email").performTextInput("fixture@example.test")
        compose.onNodeWithTag("auth_email").performImeAction()
        compose.onNodeWithTag("auth_password").performTextInput("Fixture password")
        compose.onNodeWithTag("auth_password").performImeAction()
        compose.onNodeWithText("server-fixture").assertExists()
        compose.onNodeWithTag("auth_logout").performScrollTo().performClick()
        compose.onNodeWithText("server-fixture").assertDoesNotExist()
        compose.onNodeWithTag("auth_open_login").assertExists()
    }
    @Test fun confirmationRequiredDoesNotDisplayAuthenticatedAccount() {
        screen(AuthPage.REGISTER)
        compose.onNodeWithTag("auth_email").performTextInput("fixture@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("Fixture password 123!")
        compose.onNodeWithTag("auth_confirmation").performScrollTo().performTextInput("Fixture password 123!")
        compose.onNodeWithTag("auth_confirmation").performImeAction()
        compose.onNodeWithTag("auth_back_login").performScrollTo().assertExists()
        compose.onNodeWithTag("auth_logout").assertDoesNotExist()
        compose.onNodeWithTag("auth_password").assertDoesNotExist()
    }
    @Test fun offlineLocalPlayRemainsAvailableWhenUnconfigured() {
        var back = false
        compose.setContent {
            LocalizedContent(AppLanguage.ENGLISH) {
                BackgammonTheme(ThemeRegistry.classic) {
                    AuthScreen(AuthUiState(AuthState.Unconfigured), {}, { back = true })
                }
            }
        }
        compose.onNodeWithTag("auth_local_play").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(back) }
    }
    @Test fun accountNavigationReturnsToOriginalBoardWithoutAuthentication() {
        compose.setContent {
            BackgammonApp(SettingsUiState(loaded = true), ThemeRegistry.all, {}, {}, {}, {},
                authUi = AuthUiState(AuthState.SignedOut), onAuthAction = {})
        }
        compose.onNodeWithTag("open_account").performClick()
        compose.onNodeWithTag("auth_local_play").performScrollTo().performClick()
        compose.onNodeWithTag("open_account").assertExists()
        compose.onNodeWithText("Settings").assertExists()
    }
    @Test fun georgianRegistrationRemainsScrollableAtLargeTextScale() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                LocalizedContent(AppLanguage.GEORGIAN) {
                    BackgammonTheme(ThemeRegistry.ocean) {
                        AuthScreen(AuthUiState(AuthState.SignedOut, AuthPage.REGISTER), {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("auth_confirmation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("auth_local_play").performScrollTo().assertIsDisplayed()
    }
    @Test fun encryptedStorageSurvivesNewInstanceAndRejectsTampering() = runBlocking {
        // Isolated file and Keystore alias: never overwrite the target app's real session.
        val context = object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getPackageName(): String = super.getPackageName() + ".auth.fixture"
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                super.getSharedPreferences(name + "_test", mode)
        }
        val first = EncryptedAuthStorage(context)
        first.clear()
        try {
            first.write("session", "fixture-token-not-real")
            val preferences = context.getSharedPreferences("auth_secure_v1", 0)
            assertFalse(preferences.getString("session", "")!!.contains("fixture-token"))
            assertEquals("fixture-token-not-real", EncryptedAuthStorage(context).read("session"))
            preferences.edit().putString("session", "corrupt").commit()
            try { first.read("session"); fail("Tampering must fail closed") } catch (_: AuthStorageException) { }
            assertNull(EncryptedAuthStorage(context).read("session"))
        } finally { first.clear() }
    }
}
