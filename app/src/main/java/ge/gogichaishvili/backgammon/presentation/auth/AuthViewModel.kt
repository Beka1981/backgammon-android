package ge.gogichaishvili.backgammon.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ge.gogichaishvili.backgammon.domain.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class AuthPage { WELCOME, LOGIN, REGISTER, CONFIRM, FORGOT, RESET_SENT, RESET_PASSWORD, ACCOUNT }
enum class AuthNotice { CONFIRM_SENT, RESET_SENT, PASSWORD_CHANGED, LOGIN_AFTER_CONFIRM, LOCAL_LOGOUT }
data class AuthUiState(
    val session: AuthState = AuthState.Loading,
    val page: AuthPage = AuthPage.WELCOME,
    val busy: Boolean = false,
    val issue: AuthIssue? = null,
    val notice: AuthNotice? = null,
    val formVersion: Long = 0,
    val openRequested: Boolean = false,
)
sealed interface AuthAction {
    data class Page(val page: AuthPage) : AuthAction
    class Login(val email: String, val password: String) : AuthAction
    class Register(val email: String, val password: String, val confirmation: String) : AuthAction
    class RequestReset(val email: String) : AuthAction
    class NewPassword(val password: String, val confirmation: String) : AuthAction
    class Callback(val url: String) : AuthAction
    data object Logout : AuthAction
    data object Refresh : AuthAction
    data object NavigationHandled : AuthAction
}

/** No passwords/tokens in saved state. Admission is immediate, before launching a coroutine. */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState(session = repository.state.value))
    val state = mutableState.asStateFlow()
    private val inFlight = AtomicBoolean()
    private var pendingCallback: String? = null

    init {
        viewModelScope.launch {
            repository.state.collect { session ->
                mutableState.update { old ->
                    old.forSession(session)
                }
            }
        }
    }
    private fun AuthUiState.forSession(session: AuthState): AuthUiState {
        val nextPage = when (session) {
            is AuthState.SignedIn -> if (session.recoveryRequired) AuthPage.RESET_PASSWORD else AuthPage.ACCOUNT
            else -> if (page in setOf(AuthPage.ACCOUNT, AuthPage.RESET_PASSWORD)) AuthPage.WELCOME else page
        }
        return copy(session = session, page = nextPage,
            issue = (session as? AuthState.RefreshFailed)?.issue ?: issue)
    }
    fun dispatch(action: AuthAction) {
        when (action) {
            AuthAction.NavigationHandled -> { mutableState.update { it.copy(openRequested = false) }; return }
            is AuthAction.Page -> {
                if (inFlight.get()) return
                val session = state.value.session
                val page = when (session) {
                    is AuthState.SignedIn -> if (session.recoveryRequired) AuthPage.RESET_PASSWORD else AuthPage.ACCOUNT
                    else -> if (action.page in setOf(AuthPage.ACCOUNT, AuthPage.RESET_PASSWORD)) AuthPage.WELCOME else action.page
                }
                mutableState.update { it.copy(page = page, issue = null, notice = null, formVersion = it.formVersion + 1) }
                return
            }
            else -> Unit
        }
        if (!inFlight.compareAndSet(false, true)) {
            if (action is AuthAction.Callback) pendingCallback = action.url
            return
        }
        val validation = when (action) {
            is AuthAction.Login -> AuthValidation.login(action.email, action.password)
            is AuthAction.Register -> AuthValidation.registration(action.email, action.password, action.confirmation)
            is AuthAction.RequestReset -> if (AuthValidation.email(action.email)) null else AuthIssue.INVALID_EMAIL
            is AuthAction.NewPassword -> AuthValidation.registration("validation@example.test", action.password, action.confirmation)
            else -> null
        }
        if (validation != null) {
            inFlight.set(false)
            mutableState.update { it.copy(issue = validation, notice = null) }
            return
        }
        mutableState.update { it.copy(busy = true, issue = null, notice = null,
            openRequested = it.openRequested || action is AuthAction.Callback) }
        viewModelScope.launch {
            try {
                val result = when (action) {
                    is AuthAction.Login -> repository.login(action.email, action.password)
                    is AuthAction.Register -> repository.register(action.email, action.password)
                    is AuthAction.RequestReset -> repository.requestPasswordReset(action.email)
                    is AuthAction.NewPassword -> repository.updatePassword(action.password)
                    is AuthAction.Callback -> repository.handleCallback(action.url)
                    AuthAction.Logout -> repository.logout()
                    AuthAction.Refresh -> repository.refresh()
                    else -> return@launch
                }
                mutableState.update { old ->
                    when (result) {
                        is AuthResult.Failure -> old.copy(issue = if (action == AuthAction.Logout &&
                            repository.state.value == AuthState.SignedOut) null else result.issue,
                            // Even offline logout clears the local identity; explain remote revocation uncertainty.
                            notice = if (action == AuthAction.Logout && repository.state.value == AuthState.SignedOut)
                                AuthNotice.LOCAL_LOGOUT else null)
                        AuthResult.ConfirmationSent -> old.copy(
                            page = if (action is AuthAction.RequestReset) AuthPage.RESET_SENT else AuthPage.CONFIRM,
                            notice = if (action is AuthAction.RequestReset) AuthNotice.RESET_SENT else AuthNotice.CONFIRM_SENT,
                            formVersion = old.formVersion + 1)
                        AuthResult.Success -> old.copy(issue = null, formVersion = old.formVersion + 1,
                            notice = when (action) {
                                is AuthAction.NewPassword -> AuthNotice.PASSWORD_CHANGED
                                AuthAction.Refresh -> if (repository.state.value == AuthState.SignedOut) AuthNotice.LOGIN_AFTER_CONFIRM else null
                                else -> null
                            })
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(issue = AuthIssue.UNKNOWN) } }
            finally {
                inFlight.set(false)
                mutableState.update { it.forSession(repository.state.value).copy(busy = false) }
                pendingCallback?.let { pendingCallback = null; dispatch(AuthAction.Callback(it)) }
            }
        }
    }
}
