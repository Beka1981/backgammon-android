package ge.gogichaishvili.backgammon.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.domain.profile.*
import ge.gogichaishvili.backgammon.presentation.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

fun profileFixture() = PublicProfile("40000000-0000-0000-0000-000000000001","player_1","გიორგი",
    createdAt="2026-11-12T00:00:00Z")
class ProfileFakeAuth(initial: AuthState = AuthState.SignedIn(AuthUser("fixture-owner",null))) : AuthRepository {
    override val state = MutableStateFlow(initial)
    override suspend fun login(email: String,password: String)=AuthResult.Success
    override suspend fun register(email: String,password: String)=AuthResult.ConfirmationSent
    override suspend fun logout(): AuthResult { state.value=AuthState.SignedOut; return AuthResult.Success }
    override suspend fun refresh()=AuthResult.Success
    override suspend fun requestPasswordReset(email: String)=AuthResult.ConfirmationSent
    override suspend fun updatePassword(password: String)=AuthResult.Success
    override suspend fun handleCallback(url: String)=AuthResult.Success
}
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear();Dispatchers.resetMain() }
    class Fake : ProfileRepository {
        var profile: PublicProfile? = null
        var issue: ProfileIssue? = null
        var calls=0;var loads=0;var gate: CompletableDeferred<Unit>?=null
        val commands=mutableListOf<String>()
        override suspend fun loadMine(): ProfileResult {
            loads++;gate?.await()
            return issue?.let { ProfileResult.Failure(it) } ?: profile?.let { ProfileResult.Found(it) } ?: ProfileResult.Missing
        }
        override suspend fun create(draft: ProfileDraft): ProfileResult {
            calls++;gate?.await()
            issue?.let { return ProfileResult.Failure(it) }
            if(profile==null)profile=profileFixture().copy(username=draft.username,displayName=draft.displayName,avatarId=draft.avatarId)
            return ProfileResult.Found(profile!!)
        }
        override suspend fun update(draft: ProfileDraft,expectedVersion: Long,commandId: String): ProfileResult {
            calls++;commands+=commandId;gate?.await()
            issue?.let { return ProfileResult.Failure(it) }
            profile=profile!!.copy(username=draft.username,displayName=draft.displayName,avatarId=draft.avatarId,version=expectedVersion+1)
            return ProfileResult.Found(profile!!)
        }
    }
    private fun model(repo: Fake=Fake(),auth: ProfileFakeAuth=ProfileFakeAuth(),saved: SavedStateHandle=SavedStateHandle()) =
        ProfileViewModel(auth,repo,saved).also { store.put("profile",it) }
    private fun valid(vm: ProfileViewModel) {
        vm.dispatch(ProfileAction.Username(" Player_1 "));vm.dispatch(ProfileAction.DisplayName(" გიორგი "))
    }
    @Test fun signedOutDoesNotLoadOrBlockOffline()=runTest {
        val repo=Fake();val vm=model(repo,ProfileFakeAuth(AuthState.SignedOut));runCurrent()
        assertEquals(0,repo.loads);assertNull(vm.state.value.profile);assertFalse(vm.state.value.busy)
    }
    @Test fun missingProfileOpensOnboarding()=runTest {
        val vm=model();runCurrent();assertEquals(ProfilePage.SETUP,vm.state.value.page);assertTrue(vm.state.value.openRequested)
        vm.dispatch(ProfileAction.NavigationHandled);assertFalse(vm.state.value.openRequested)
    }
    @Test fun existingProfileLoadsWithoutCreating()=runTest {
        val repo=Fake().apply { profile=profileFixture() };val vm=model(repo);runCurrent()
        assertEquals(ProfilePage.MY_PROFILE,vm.state.value.page);assertEquals(0,repo.calls)
    }
    @Test fun invalidFormNeverReachesService()=runTest {
        val repo=Fake();val vm=model(repo);runCurrent();vm.dispatch(ProfileAction.Save);runCurrent()
        assertEquals(ProfileIssue.USERNAME_INVALID,vm.state.value.issue);assertEquals(0,repo.calls)
    }
    @Test fun canonicalCreationAndZeroStatistics()=runTest {
        val vm=model();runCurrent();valid(vm);vm.dispatch(ProfileAction.Save);runCurrent()
        assertEquals("player_1",vm.state.value.profile!!.username);assertEquals("გიორგი",vm.state.value.profile!!.displayName)
        assertEquals(0,vm.state.value.profile!!.gamesPlayed);assertEquals(ProfilePage.MY_PROFILE,vm.state.value.page)
    }
    @Test fun duplicateSubmissionsAdmittedOnce()=runTest {
        val repo=Fake();val vm=model(repo);runCurrent();valid(vm);repo.gate=CompletableDeferred()
        repeat(20){ vm.dispatch(ProfileAction.Save) };runCurrent();assertEquals(1,repo.calls)
        repo.gate!!.complete(Unit);runCurrent();assertFalse(vm.state.value.busy)
    }
    @Test fun usernameConflictPreservesDraft()=runTest {
        val repo=Fake().apply { issue=null };val vm=model(repo);runCurrent();valid(vm)
        repo.issue=ProfileIssue.USERNAME_UNAVAILABLE;vm.dispatch(ProfileAction.Save);runCurrent()
        assertEquals(ProfileIssue.USERNAME_UNAVAILABLE,vm.state.value.issue);assertEquals(" Player_1 ",vm.state.value.draft.username)
    }
    @Test fun loadingNetworkFailureCanRetry()=runTest {
        val repo=Fake().apply { issue=ProfileIssue.NETWORK };val vm=model(repo);runCurrent()
        assertEquals(ProfilePage.ERROR,vm.state.value.page);repo.issue=null;vm.dispatch(ProfileAction.Retry);runCurrent()
        assertEquals(ProfilePage.SETUP,vm.state.value.page)
    }
    @Test fun editCommitsOnlyAcceptedProfile()=runTest {
        val repo=Fake().apply { profile=profileFixture() };val vm=model(repo);runCurrent()
        vm.dispatch(ProfileAction.Edit);vm.dispatch(ProfileAction.DisplayName("New"));repo.gate=CompletableDeferred()
        vm.dispatch(ProfileAction.Save);runCurrent();assertEquals("გიორგი",vm.state.value.profile!!.displayName)
        repo.gate!!.complete(Unit);runCurrent();assertEquals("New",vm.state.value.profile!!.displayName)
        assertEquals(1L,vm.state.value.profile!!.version)
    }
    @Test fun cancelEditKeepsServerData()=runTest {
        val vm=model(Fake().apply { profile=profileFixture() });runCurrent()
        vm.dispatch(ProfileAction.Edit);vm.dispatch(ProfileAction.DisplayName("Discard"));vm.dispatch(ProfileAction.Cancel)
        assertEquals("გიორგი",vm.state.value.draft.displayName);assertEquals(ProfilePage.MY_PROFILE,vm.state.value.page)
    }
    @Test fun avatarPreviewCancelAndSaveAreSeparate()=runTest {
        val vm=model();runCurrent();vm.dispatch(ProfileAction.OpenAvatars);vm.dispatch(ProfileAction.PreviewAvatar("moon"))
        assertEquals("crown",vm.state.value.draft.avatarId);vm.dispatch(ProfileAction.Cancel)
        assertEquals("crown",vm.state.value.draft.avatarId)
        vm.dispatch(ProfileAction.OpenAvatars);vm.dispatch(ProfileAction.PreviewAvatar("star"));vm.dispatch(ProfileAction.SaveAvatar)
        assertEquals("star",vm.state.value.draft.avatarId);assertNull(vm.state.value.profile)
    }
    @Test fun invalidAvatarSelectionIgnored()=runTest {
        val vm=model();runCurrent();vm.dispatch(ProfileAction.OpenAvatars)
        vm.dispatch(ProfileAction.PreviewAvatar("https://evil.invalid"));assertEquals("crown",vm.state.value.avatarPreview)
    }
    @Test fun versionConflictRequiresExplicitReload()=runTest {
        val repo=Fake().apply { profile=profileFixture() };val vm=model(repo);runCurrent()
        vm.dispatch(ProfileAction.Edit);vm.dispatch(ProfileAction.DisplayName("Draft"));repo.issue=ProfileIssue.VERSION_CONFLICT
        vm.dispatch(ProfileAction.Save);runCurrent();assertEquals(ProfileIssue.VERSION_CONFLICT,vm.state.value.issue)
        repo.issue=null;repo.profile=profileFixture().copy(displayName="Remote",version=4)
        vm.dispatch(ProfileAction.Retry);runCurrent();assertEquals("Remote",vm.state.value.draft.displayName)
        assertEquals(ProfilePage.MY_PROFILE,vm.state.value.page)
    }
    @Test fun networkRetryReusesMutationCommand()=runTest {
        val repo=Fake().apply { profile=profileFixture() };val vm=model(repo);runCurrent();vm.dispatch(ProfileAction.Edit)
        repo.issue=ProfileIssue.NETWORK;vm.dispatch(ProfileAction.Save);runCurrent()
        repo.issue=null;vm.dispatch(ProfileAction.Save);runCurrent()
        assertEquals(2,repo.commands.size);assertEquals(repo.commands[0],repo.commands[1])
    }
    @Test fun authExpiryClearsProfileAndLateResponse()=runTest {
        val repo=Fake().apply { gate=CompletableDeferred();profile=profileFixture() }
        val auth=ProfileFakeAuth();val vm=model(repo,auth);runCurrent()
        auth.state.value=AuthState.SignedOut;runCurrent();repo.gate!!.complete(Unit);runCurrent()
        assertNull(vm.state.value.profile);assertFalse(vm.state.value.openRequested)
    }
    @Test fun accountSwitchDoesNotLeakDraft()=runTest {
        val auth=ProfileFakeAuth();val vm=model(auth=auth);runCurrent();valid(vm)
        auth.state.value=AuthState.SignedIn(AuthUser("other-owner",null));runCurrent()
        assertEquals("",vm.state.value.draft.username)
    }
    @Test fun recoverySessionCannotLoadProfile()=runTest {
        val repo=Fake();model(repo,ProfileFakeAuth(AuthState.SignedIn(AuthUser("owner",null),true)));runCurrent()
        assertEquals(0,repo.loads)
    }
    @Test fun processRestoresOnlySameOwnerDraft()=runTest {
        val saved=SavedStateHandle();var vm=model(saved=saved);runCurrent();valid(vm)
        vm.dispatch(ProfileAction.OpenAvatars);vm.dispatch(ProfileAction.PreviewAvatar("lotus"));vm.dispatch(ProfileAction.SaveAvatar)
        store.clear();vm=model(saved=saved);runCurrent()
        assertEquals(" Player_1 ",vm.state.value.draft.username);assertEquals("lotus",vm.state.value.draft.avatarId)
    }
    @Test fun processRestoresEditDraftButReloadsAuthoritativeProfile()=runTest {
        val saved=SavedStateHandle();val repo=Fake().apply { profile=profileFixture() }
        var vm=model(repo,saved=saved);runCurrent();vm.dispatch(ProfileAction.Edit);vm.dispatch(ProfileAction.DisplayName("Draft"))
        store.clear();repo.profile=profileFixture().copy(version=2);vm=model(repo,saved=saved);runCurrent()
        assertEquals(ProfilePage.EDIT,vm.state.value.page);assertEquals("Draft",vm.state.value.draft.displayName)
        assertEquals(2L,vm.state.value.profile!!.version)
        assertEquals(0L,saved.get<Long>("profile.expected")) // Draft began at version 0, not the newly loaded version.
    }
}
