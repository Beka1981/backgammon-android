package ge.gogichaishvili.backgammon.data.auth

import ge.gogichaishvili.backgammon.domain.auth.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.MessageDigest

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    private val storage: AuthStorage,
    scope: CoroutineScope,
    private val projectIdentity: String,
    private val callbackScheme: String = "ge.gogichaishvili.backgammon",
) : AuthRepository {
    private val auth = client.auth
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val state = mutableState.asStateFlow()
    private val gate = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private var storageBlocked = false
    private var startupStatus: SessionStatus? = null

    init {
        scope.launch {
            gate.withLock {
                try {
                    // Do not send a saved refresh token/verifier to a different Supabase project.
                    if (storage.read("project") != projectIdentity) {
                        storage.clear()
                        storage.write("project", projectIdentity)
                    }
                    if (auth.loadFromStorage(autoRefresh = false)) {
                        auth.refreshCurrentSession()
                        publish()
                        auth.startAutoRefreshForCurrentSession()
                    } else mutableState.value = AuthState.SignedOut
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    if (error is AuthStorageException) storageBlocked = true
                    if (issue(error) == AuthIssue.INVALID_CREDENTIALS) {
                        runCatching { auth.clearSession() }
                        mutableState.value = AuthState.SignedOut
                    } else mutableState.value = AuthState.RefreshFailed(cachedUser(), issue(error))
                } finally { startupStatus = auth.sessionStatus.value; ready.complete(Unit) }
            }
        }
        scope.launch {
            ready.await()
            auth.sessionStatus.collect { status ->
                gate.withLock {
                    if (storageBlocked) return@withLock
                    // Do not turn a failed startup refresh into SignedIn from its cached SDK status.
                    if (status === startupStatus) { startupStatus = null; return@withLock }
                    startupStatus = null
                    try {
                        when (status) {
                            is SessionStatus.Authenticated -> publish()
                            is SessionStatus.RefreshFailure -> mutableState.value = AuthState.RefreshFailed(cachedUser())
                            is SessionStatus.NotAuthenticated -> mutableState.value = AuthState.SignedOut
                            SessionStatus.Initializing -> Unit
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        val reason = issue(error)
                        if (reason == AuthIssue.STORAGE) {
                            storageBlocked = true
                            auth.stopAutoRefreshForCurrentSession()
                        }
                        mutableState.value = AuthState.RefreshFailed(null, reason)
                    }
                }
            }
        }
    }
    private fun cachedUser(): AuthUser? = auth.currentUserOrNull()?.let { AuthUser(it.id, it.email) }
    private suspend fun publish() {
        val user = cachedUser()
        mutableState.value = if (user == null) AuthState.SignedOut
            else AuthState.SignedIn(user, storage.read("recovery") == "true")
    }
    private suspend fun operation(block: suspend () -> AuthResult): AuthResult {
        ready.await()
        return gate.withLock {
            if (storageBlocked) return@withLock AuthResult.Failure(AuthIssue.STORAGE)
            try { block() } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                val reason = issue(error)
                if (reason == AuthIssue.STORAGE) {
                    storageBlocked = true
                    auth.stopAutoRefreshForCurrentSession()
                    mutableState.value = AuthState.RefreshFailed(null, AuthIssue.STORAGE)
                }
                AuthResult.Failure(reason)
            }
        }
    }
    override suspend fun login(email: String, password: String) = operation {
        AuthValidation.login(email, password)?.let { return@operation AuthResult.Failure(it) }
        if ((state.value as? AuthState.SignedIn)?.recoveryRequired == true)
            return@operation AuthResult.Failure(AuthIssue.INVALID_LINK)
        auth.signInWith(Email) { this.email = email.trim(); this.password = password }
        storage.write("recovery", null)
        storage.write("pending_flow", null)
        publish()
        AuthResult.Success
    }
    override suspend fun register(email: String, password: String) = operation {
        AuthValidation.registration(email, password, password)?.let { return@operation AuthResult.Failure(it) }
        storage.write("pending_flow", EmailFlow.CONFIRM.name)
        try {
            auth.signUpWith(Email, redirectUrl = "$callbackScheme://auth/confirm") {
                this.email = email.trim(); this.password = password
            }
        } catch (error: AuthRestException) {
            // Supabase may deliberately conceal an existing identity. Never enumerate it here.
            if (error.error !in setOf("user_already_exists", "email_exists")) throw error
        }
        if (auth.currentUserOrNull() != null) {
            storage.write("recovery", null)
            publish()
            AuthResult.Success
        } else AuthResult.ConfirmationSent
    }
    override suspend fun requestPasswordReset(email: String) = operation {
        if (!AuthValidation.email(email)) return@operation AuthResult.Failure(AuthIssue.INVALID_EMAIL)
        storage.write("pending_flow", EmailFlow.RECOVERY.name)
        try { auth.resetPasswordForEmail(email.trim(), redirectUrl = "$callbackScheme://auth/recovery") }
        catch (error: AuthRestException) {
            if (error.error !in setOf("user_not_found", "email_not_found")) throw error
        }
        AuthResult.ConfirmationSent
    }
    override suspend fun handleCallback(url: String) = operation {
        val callback = AuthCallback.parse(url, callbackScheme) ?: return@operation AuthResult.Failure(AuthIssue.INVALID_LINK)
        val digest = MessageDigest.getInstance("SHA-256").digest(callback.code.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (storage.read("last_callback") == digest) return@operation AuthResult.Success
        if (storage.read("pending_flow") != callback.flow.name ||
            auth.codeVerifierCache.loadCodeVerifier() == null)
            return@operation AuthResult.Failure(AuthIssue.INVALID_LINK)
        val session = auth.exchangeCodeForSession(callback.code, saveSession = false)
        storage.write("recovery", if (callback.flow == EmailFlow.RECOVERY) "true" else null)
        auth.importSession(session)
        storage.write("last_callback", digest)
        storage.write("pending_flow", null)
        publish()
        AuthResult.Success
    }
    override suspend fun updatePassword(password: String) = operation {
        if ((state.value as? AuthState.SignedIn)?.recoveryRequired != true)
            return@operation AuthResult.Failure(AuthIssue.INVALID_LINK)
        AuthValidation.registration("valid@example.test", password, password)?.let {
            return@operation AuthResult.Failure(it)
        }
        auth.updateUser { this.password = password }
        storage.write("recovery", null)
        publish()
        AuthResult.Success
    }
    override suspend fun refresh() = operation {
        if (auth.currentSessionOrNull() == null && !auth.loadFromStorage(autoRefresh = false)) {
            mutableState.value = AuthState.SignedOut
            AuthResult.Success
        } else {
            try {
                auth.refreshCurrentSession()
                publish()
                auth.startAutoRefreshForCurrentSession()
                AuthResult.Success
            } catch (error: AuthRestException) {
                if (issue(error) == AuthIssue.INVALID_CREDENTIALS) {
                    auth.clearSession()
                    mutableState.value = AuthState.SignedOut
                }
                throw error
            }
        }
    }
    override suspend fun logout(): AuthResult {
        ready.await()
        return gate.withLock {
            auth.stopAutoRefreshForCurrentSession()
            // Revocation is best effort offline; local identity must still be removed.
            val remote = runCatching { withTimeout(10000) { auth.signOut(SignOutScope.LOCAL) } }
            withContext(NonCancellable) {
            try {
                storage.clear()
                storage.write("project", projectIdentity)
                auth.clearSession()
                storageBlocked = false
                mutableState.value = AuthState.SignedOut
                if (remote.isFailure) AuthResult.Failure(AuthIssue.NETWORK) else AuthResult.Success
            } catch (_: Exception) {
                storageBlocked = true
                mutableState.value = AuthState.RefreshFailed(null, AuthIssue.STORAGE)
                AuthResult.Failure(AuthIssue.STORAGE)
            }
            }
        }
    }
    private fun issue(error: Exception): AuthIssue = when (error) {
        is AuthStorageException -> AuthIssue.STORAGE
        is AuthRestException -> when (error.error) {
            "invalid_credentials", "bad_jwt", "session_not_found", "session_expired",
            "refresh_token_not_found", "refresh_token_already_used", "user_banned" -> AuthIssue.INVALID_CREDENTIALS
            "email_not_confirmed" -> AuthIssue.EMAIL_UNCONFIRMED
            "weak_password", "same_password" -> AuthIssue.WEAK_PASSWORD
            "over_request_rate_limit", "over_email_send_rate_limit", "over_refresh_token_rate_limit" -> AuthIssue.RATE_LIMITED
            "flow_state_not_found", "flow_state_expired", "bad_code_verifier", "otp_expired" -> AuthIssue.INVALID_LINK
            else -> AuthIssue.UNKNOWN
        }
        is IOException, is HttpRequestException, is HttpRequestTimeoutException -> AuthIssue.NETWORK
        else -> AuthIssue.UNKNOWN
    }
}
