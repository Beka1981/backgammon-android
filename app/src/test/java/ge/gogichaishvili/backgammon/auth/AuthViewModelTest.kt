package ge.gogichaishvili.backgammon.auth

import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.presentation.auth.*
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private fun model(repo: Fake = Fake()): AuthViewModel = AuthViewModel(repo).also { store.put("auth", it) }
    private val email = "fixture@example.test"
    private val password = "Fixture password 123!"

    private class Fake(initial: AuthState = AuthState.SignedOut) : AuthRepository {
        override val state = MutableStateFlow(initial)
        var calls = 0
        var release: CompletableDeferred<Unit>? = null
        var result: AuthResult = AuthResult.Success
        var confirmation = true
        var recover = true
        private suspend fun response(): AuthResult { calls++; release?.await(); return result }
        override suspend fun login(email: String, password: String): AuthResult {
            val answer = response()
            if (answer == AuthResult.Success) state.value = AuthState.SignedIn(AuthUser("server-user", email))
            return answer
        }
        override suspend fun register(email: String, password: String): AuthResult {
            val answer = response()
            if (answer is AuthResult.Failure) return answer
            return if (confirmation) AuthResult.ConfirmationSent else {
                state.value = AuthState.SignedIn(AuthUser("server-user", email)); AuthResult.Success
            }
        }
        override suspend fun logout(): AuthResult { val answer = response(); state.value = AuthState.SignedOut; return answer }
        override suspend fun refresh() = response()
        override suspend fun requestPasswordReset(email: String): AuthResult {
            val answer = response()
            return if (answer is AuthResult.Failure) answer else AuthResult.ConfirmationSent
        }
        override suspend fun updatePassword(password: String): AuthResult {
            val answer = response()
            if (answer == AuthResult.Success) state.value = AuthState.SignedIn(AuthUser("server-user", "fixture@example.test"))
            return answer
        }
        override suspend fun handleCallback(url: String): AuthResult {
            val answer = response()
            if (answer == AuthResult.Success) state.value = AuthState.SignedIn(AuthUser("server-user", "fixture@example.test"), recover)
            return answer
        }
    }
    @Test fun invalidRegistrationNeverCallsService() = runTest {
        val repo = Fake(); val vm = model(repo)
        vm.dispatch(AuthAction.Register("invalid", password, password)); runCurrent()
        assertEquals(AuthIssue.INVALID_EMAIL, vm.state.value.issue); assertEquals(0, repo.calls)
    }
    @Test fun shortRegistrationPasswordRejected() = runTest {
        val repo = Fake(); val vm = model(repo)
        vm.dispatch(AuthAction.Register(email, "short", "short")); runCurrent()
        assertEquals(AuthIssue.WEAK_PASSWORD, vm.state.value.issue); assertEquals(0, repo.calls)
    }
    @Test fun confirmationMustMatch() = runTest {
        val repo = Fake(); val vm = model(repo)
        vm.dispatch(AuthAction.Register(email, password, "different")); runCurrent()
        assertEquals(AuthIssue.PASSWORD_MISMATCH, vm.state.value.issue); assertEquals(0, repo.calls)
    }
    @Test fun loginAcceptsExistingShortPasswordAndUsesServerIdentity() = runTest {
        val vm = model()
        vm.dispatch(AuthAction.Login(email, "old")); runCurrent()
        assertEquals(AuthPage.ACCOUNT, vm.state.value.page)
        assertEquals("server-user", (vm.state.value.session as AuthState.SignedIn).user.id)
    }
    @Test fun invalidCredentialsStayOutOfAccountScreen() = runTest {
        val vm = model(Fake().apply { result = AuthResult.Failure(AuthIssue.INVALID_CREDENTIALS) })
        vm.dispatch(AuthAction.Login(email, password)); runCurrent()
        assertEquals(AuthState.SignedOut, vm.state.value.session)
        assertEquals(AuthIssue.INVALID_CREDENTIALS, vm.state.value.issue)
        assertNotEquals(AuthPage.ACCOUNT, vm.state.value.page)
    }
    @Test fun networkFailureIsRetryableAndNotBusyForever() = runTest {
        val repo = Fake().apply { result = AuthResult.Failure(AuthIssue.NETWORK) }; val vm = model(repo)
        vm.dispatch(AuthAction.Login(email, password)); runCurrent()
        assertEquals(AuthIssue.NETWORK, vm.state.value.issue); assertFalse(vm.state.value.busy)
        repo.result = AuthResult.Success
        vm.dispatch(AuthAction.Login(email, password)); runCurrent()
        assertEquals(AuthPage.ACCOUNT, vm.state.value.page)
    }
    @Test fun repeatedLoginAdmittedOnlyOnceBeforeCoroutineStarts() = runTest {
        val repo = Fake().apply { release = CompletableDeferred() }; val vm = model(repo)
        repeat(20) { vm.dispatch(AuthAction.Login(email, password)) }
        runCurrent(); assertEquals(1, repo.calls); assertTrue(vm.state.value.busy)
        repo.release!!.complete(Unit); runCurrent(); assertFalse(vm.state.value.busy)
    }
    @Test fun repeatedRegisterDoesNotSendDuplicateEmails() = runTest {
        val repo = Fake().apply { release = CompletableDeferred() }; val vm = model(repo)
        repeat(10) { vm.dispatch(AuthAction.Register(email, password, password)) }
        runCurrent(); assertEquals(1, repo.calls)
        repo.release!!.complete(Unit); runCurrent(); assertEquals(AuthPage.CONFIRM, vm.state.value.page)
    }
    @Test fun confirmationRequiredDoesNotInventSignedInSession() = runTest {
        val vm = model()
        vm.dispatch(AuthAction.Register(email, password, password)); runCurrent()
        assertEquals(AuthPage.CONFIRM, vm.state.value.page)
        assertEquals(AuthState.SignedOut, vm.state.value.session)
        assertEquals(AuthNotice.CONFIRM_SENT, vm.state.value.notice)
    }
    @Test fun autoConfirmedServerSessionIsObservedWhenEnabled() = runTest {
        val vm = model(Fake().apply { confirmation = false })
        vm.dispatch(AuthAction.Register(email, password, password)); runCurrent()
        assertEquals(AuthPage.ACCOUNT, vm.state.value.page)
    }
    @Test fun logoutRemovesAccountScreenWithoutTouchingOfflineRepositories() = runTest {
        val repo = Fake(AuthState.SignedIn(AuthUser("restored-user", email))); val vm = model(repo)
        runCurrent(); vm.dispatch(AuthAction.Logout); runCurrent()
        assertEquals(AuthState.SignedOut, vm.state.value.session)
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
        vm.dispatch(AuthAction.Page(AuthPage.ACCOUNT))
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
    }
    @Test fun offlineLogoutStillClearsIdentityAndExplainsRevocation() = runTest {
        val repo = Fake(AuthState.SignedIn(AuthUser("restored-user", email))).apply {
            result = AuthResult.Failure(AuthIssue.NETWORK)
        }; val vm = model(repo)
        vm.dispatch(AuthAction.Logout); runCurrent()
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
        assertEquals(AuthNotice.LOCAL_LOGOUT, vm.state.value.notice)
    }
    @Test fun restoredSessionDoesNotNeedAnotherLoginAction() = runTest {
        val repo = Fake(AuthState.SignedIn(AuthUser("restored-user", email))); val vm = model(repo)
        runCurrent()
        assertEquals(AuthPage.ACCOUNT, vm.state.value.page); assertEquals(0, repo.calls)
    }
    @Test fun sessionExpirationClosesAccountPage() = runTest {
        val repo = Fake(AuthState.SignedIn(AuthUser("restored-user", email))); val vm = model(repo)
        runCurrent(); repo.state.value = AuthState.SignedOut; runCurrent()
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
    }
    @Test fun expiredOrOfflineSessionDoesNotGrantAccountScreen() = runTest {
        val vm = model(Fake(AuthState.RefreshFailed(AuthUser("cached-user", email))))
        runCurrent()
        assertNotEquals(AuthPage.ACCOUNT, vm.state.value.page)
        assertEquals(AuthIssue.NETWORK, vm.state.value.issue)
    }
    @Test fun resetRequestHasGenericConfirmation() = runTest {
        val vm = model()
        vm.dispatch(AuthAction.RequestReset(email)); runCurrent()
        assertEquals(AuthPage.RESET_SENT, vm.state.value.page)
        assertEquals(AuthNotice.RESET_SENT, vm.state.value.notice)
        assertEquals(AuthState.SignedOut, vm.state.value.session)
    }
    @Test fun resetNetworkFailureDoesNotShowFalseEmailSuccess() = runTest {
        val vm = model(Fake().apply { result = AuthResult.Failure(AuthIssue.NETWORK) })
        vm.dispatch(AuthAction.RequestReset(email)); runCurrent()
        assertEquals(AuthIssue.NETWORK, vm.state.value.issue)
        assertNotEquals(AuthPage.RESET_SENT, vm.state.value.page)
    }
    @Test fun restoredRecoverySessionCannotNavigateToAccount() = runTest {
        val vm = model(Fake(AuthState.SignedIn(AuthUser("server-user", email), true)))
        runCurrent(); vm.dispatch(AuthAction.Page(AuthPage.ACCOUNT))
        assertEquals(AuthPage.RESET_PASSWORD, vm.state.value.page)
    }
    @Test fun recoveryCallbackRequestsAuthNavigationAndForcesReset() = runTest {
        val vm = model()
        vm.dispatch(AuthAction.Callback(AuthCallback.recoveryUrl + "?code=fixture-code")); runCurrent()
        assertTrue(vm.state.value.openRequested); assertEquals(AuthPage.RESET_PASSWORD, vm.state.value.page)
        vm.dispatch(AuthAction.NavigationHandled); assertFalse(vm.state.value.openRequested)
    }
    @Test fun passwordChangeReleasesRecoveryGate() = runTest {
        val vm = model(Fake(AuthState.SignedIn(AuthUser("server-user", email), true)))
        runCurrent(); vm.dispatch(AuthAction.NewPassword(password, password)); runCurrent()
        assertEquals(AuthPage.ACCOUNT, vm.state.value.page)
        assertEquals(AuthNotice.PASSWORD_CHANGED, vm.state.value.notice)
    }
    @Test fun callbackQueuedDuringRequestIsNotDropped() = runTest {
        val repo = Fake().apply { release = CompletableDeferred() }; val vm = model(repo)
        vm.dispatch(AuthAction.RequestReset(email)); runCurrent()
        vm.dispatch(AuthAction.Callback(AuthCallback.recoveryUrl + "?code=fixture-code"))
        repo.release!!.complete(Unit); runCurrent()
        assertEquals(2, repo.calls); assertEquals(AuthPage.RESET_PASSWORD, vm.state.value.page)
    }
    @Test fun passwordsAreAbsentFromUiAndActionDescriptions() = runTest {
        val vm = model()
        val action = AuthAction.Login(email, password)
        assertFalse(action.toString().contains(password))
        vm.dispatch(action); runCurrent()
        assertFalse(vm.state.value.toString().contains(password))
    }
    @Test fun unconfiguredStateStillHasWelcomeForOfflineNavigation() = runTest {
        val vm = model(Fake(AuthState.Unconfigured)); runCurrent()
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
        assertEquals(AuthState.Unconfigured, vm.state.value.session)
    }
}
