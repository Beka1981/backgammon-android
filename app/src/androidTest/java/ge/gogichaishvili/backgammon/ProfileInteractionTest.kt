package ge.gogichaishvili.backgammon

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.domain.profile.*
import ge.gogichaishvili.backgammon.domain.settings.AppLanguage
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.presentation.auth.*
import ge.gogichaishvili.backgammon.presentation.profile.*
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

class ProfileInteractionTest {
    @get:Rule(order=2) val compatibility=PageSizeCompatibilityRule()
    @get:Rule(order=1) val compose=createComposeRule()
    private val store=ViewModelStore()
    @After fun close(){ compose.runOnIdle { store.clear() } }
    private class Auth : AuthRepository {
        override val state=MutableStateFlow<AuthState>(AuthState.SignedIn(AuthUser("fixture-private-owner",null)))
        override suspend fun login(email:String,password:String)=AuthResult.Success
        override suspend fun register(email:String,password:String)=AuthResult.Success
        override suspend fun logout(): AuthResult { state.value=AuthState.SignedOut;return AuthResult.Success }
        override suspend fun refresh()=AuthResult.Success
        override suspend fun requestPasswordReset(email:String)=AuthResult.Success
        override suspend fun updatePassword(password:String)=AuthResult.Success
        override suspend fun handleCallback(url:String)=AuthResult.Success
    }
    private class Fake : ProfileRepository {
        var profile: PublicProfile?=null
        var issue: ProfileIssue?=null
        var calls=0
        override suspend fun loadMine(): ProfileResult = issue?.let { ProfileResult.Failure(it) }
            ?:profile?.let { ProfileResult.Found(it) }?:ProfileResult.Missing
        override suspend fun create(draft:ProfileDraft): ProfileResult {
            calls++
            profile=PublicProfile("40000000-0000-0000-0000-000000000001",draft.username,draft.displayName,
                draft.avatarId,createdAt="2026-11-12T00:00:00Z")
            return ProfileResult.Found(profile!!)
        }
        override suspend fun update(draft:ProfileDraft,expectedVersion:Long,commandId:String): ProfileResult {
            calls++;profile=profile!!.copy(username=draft.username,displayName=draft.displayName,
                avatarId=draft.avatarId,version=expectedVersion+1)
            return ProfileResult.Found(profile!!)
        }
    }
    private fun fixture()=PublicProfile("40000000-0000-0000-0000-000000000001","player_1","Player",
        createdAt="2026-11-12T00:00:00Z")
    private fun screen(repo:Fake=Fake(),language:AppLanguage=AppLanguage.ENGLISH,scale:Float=1f,
        local:()->Unit={}): ProfileViewModel {
        val auth=Auth()
        lateinit var profile:ProfileViewModel;lateinit var account:AuthViewModel
        compose.runOnUiThread {
            profile=ProfileViewModel(auth,repo);account=AuthViewModel(auth)
            store.put("profile",profile);store.put("auth",account)
        }
        compose.setContent {
            val ui by profile.state.collectAsState()
            val authUi by account.state.collectAsState()
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density,scale)) {
                LocalizedContent(language) { BackgammonTheme(ThemeRegistry.royal) {
                    if(authUi.session is AuthState.SignedIn)
                        ProfileScreen(ui,profile::dispatch,{account.dispatch(AuthAction.Logout)},local)
                    else AuthScreen(authUi,account::dispatch,local)
                } }
            }
        }
        return profile
    }
    @Test fun formValidationNeverCreatesInvalidProfile() {
        val repo=Fake();screen(repo)
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_error").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0,repo.calls) }
    }
    @Test fun createsProfileAndDisplaysOnlyPublicIdentityAndZeroStats() {
        screen()
        compose.onNodeWithTag("profile_username").performScrollTo().performTextInput("Player_1")
        compose.onNodeWithTag("profile_display_name").performScrollTo().performTextInput("Player")
        compose.onNodeWithTag("profile_display_name").performImeAction()
        compose.onNodeWithTag("profile_name").assertTextEquals("Player")
        compose.onNodeWithText("@player_1").assertExists()
        compose.onNodeWithText("Games played: 0").assertExists()
        compose.onNodeWithText("fixture-private-owner").assertDoesNotExist()
    }
    @Test fun avatarPreviewSupportsCancelThenSaveWithoutServerWrite() {
        val repo=Fake();screen(repo)
        compose.onNodeWithTag("profile_avatars").performScrollTo().performClick()
        compose.onNodeWithTag("avatar_moon").performScrollTo().assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("profile_cancel").performScrollTo().performClick()
        compose.onNodeWithTag("profile_avatar").assertContentDescriptionEquals("Crown")
        compose.onNodeWithTag("profile_avatars").performScrollTo().performClick()
        compose.onNodeWithTag("avatar_star").performScrollTo().performClick()
        compose.onNodeWithTag("avatar_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_avatar").assertContentDescriptionEquals("Star")
        compose.runOnIdle { assertEquals(0,repo.calls) }
    }
    @Test fun editCancelAndCommit() {
        screen(Fake().apply { profile=fixture() })
        compose.onNodeWithTag("profile_edit").performScrollTo().performClick()
        compose.onNodeWithTag("profile_display_name").performScrollTo().performTextReplacement("Discarded")
        compose.onNodeWithTag("profile_cancel").performScrollTo().performClick()
        compose.onNodeWithTag("profile_name").assertTextEquals("Player")
        compose.onNodeWithTag("profile_edit").performScrollTo().performClick()
        compose.onNodeWithTag("profile_display_name").performScrollTo().performTextReplacement("Updated")
        compose.onNodeWithTag("profile_display_name").performImeAction()
        compose.onNodeWithTag("profile_name").assertTextEquals("Updated")
    }
    @Test fun logoutRemovesProfileAndAllowsLocalPlay() {
        var local=false;screen(Fake().apply { profile=fixture() },local={local=true})
        compose.onNodeWithTag("profile_logout").performScrollTo().performClick()
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.onNodeWithTag("auth_local_play").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(local) }
    }
    @Test fun georgianLargeTextOnboardingAndAvatarsRemainAccessible() {
        screen(language=AppLanguage.GEORGIAN,scale=2f)
        compose.onNodeWithTag("profile_save").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("პროფილის შენახვა").assertExists()
        compose.onNodeWithTag("profile_avatars").performScrollTo().performClick()
        compose.onNodeWithTag("avatar_star").performScrollTo().assertHasClickAction().performClick()
        compose.onNodeWithTag("avatar_save").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("profile_local_play").performScrollTo().assertIsDisplayed()
    }
    @Test fun unavailableServiceOffersRetryAndOfflineExit() {
        val repo=Fake().apply { issue=ProfileIssue.UNAVAILABLE };var local=false;screen(repo,local={local=true})
        compose.onNodeWithTag("profile_error").assertExists()
        compose.onNodeWithTag("profile_local_play").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(local);repo.issue=null }
        compose.onNodeWithTag("profile_retry").performScrollTo().performClick()
        compose.onNodeWithTag("profile_username").assertExists()
    }
    @Test fun onboardingNavigationDoesNotGateOriginalBoard() {
        val auth=Auth();val repo=Fake()
        lateinit var model:ProfileViewModel
        compose.runOnUiThread { model=ProfileViewModel(auth,repo);store.put("profile",model) }
        compose.setContent {
            val profile by model.state.collectAsState()
            BackgammonApp(SettingsUiState(loaded=true),ThemeRegistry.all,{},{},{},{},
                authUi=AuthUiState(auth.state.value,AuthPage.ACCOUNT),profileUi=profile,onProfileAction=model::dispatch)
        }
        compose.onNodeWithTag("profile_username").assertExists()
        compose.onNodeWithTag("profile_local_play").performScrollTo().performClick()
        compose.onNodeWithTag("open_account").assertExists()
        compose.onNodeWithText("Settings").assertExists()
    }
    @Test fun staleProfileCannotAppearDuringAccountSwitch() {
        compose.setContent {
            BackgammonApp(SettingsUiState(loaded=true),ThemeRegistry.all,{},{},{},{},
                authUi=AuthUiState(AuthState.SignedIn(AuthUser("new-owner",null)),AuthPage.ACCOUNT),
                profileUi=ProfileUiState(ProfilePage.MY_PROFILE,profile=fixture(),ownerId="previous-owner"))
        }
        compose.onNodeWithTag("open_account").performClick()
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.onNodeWithText("@player_1").assertDoesNotExist()
        compose.onNodeWithText("Loading your profile…").assertExists()
        compose.onNodeWithTag("profile_local_play").performScrollTo().assertIsEnabled()
    }
}
