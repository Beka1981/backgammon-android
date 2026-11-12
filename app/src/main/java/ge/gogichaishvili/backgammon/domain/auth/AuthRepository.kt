package ge.gogichaishvili.backgammon.domain.auth

import kotlinx.coroutines.flow.StateFlow

/** Identity only. Tokens and SDK sessions never cross this boundary. */
data class AuthUser(val id: String, val email: String?)
sealed interface AuthState {
    data object Loading : AuthState
    data object Unconfigured : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser, val recoveryRequired: Boolean = false) : AuthState
    data class RefreshFailed(val user: AuthUser?, val issue: AuthIssue = AuthIssue.NETWORK) : AuthState
}
enum class AuthIssue {
    INVALID_EMAIL, WEAK_PASSWORD, PASSWORD_MISMATCH, INVALID_CREDENTIALS,
    EMAIL_UNCONFIRMED, NETWORK, RATE_LIMITED, INVALID_LINK, STORAGE, UNCONFIGURED, UNKNOWN
}
sealed interface AuthResult {
    data object Success : AuthResult
    data object ConfirmationSent : AuthResult
    data class Failure(val issue: AuthIssue) : AuthResult
}
interface AuthRepository {
    val state: StateFlow<AuthState>
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(email: String, password: String): AuthResult
    suspend fun logout(): AuthResult
    suspend fun refresh(): AuthResult
    suspend fun requestPasswordReset(email: String): AuthResult
    suspend fun updatePassword(password: String): AuthResult
    suspend fun handleCallback(url: String): AuthResult
}

/** A conservative client floor; Auth server policy remains authoritative. No password trimming. */
object AuthValidation {
    const val minimumPasswordLength = 12
    fun email(value: String): Boolean = value.length <= 254 &&
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value.trim())
    fun registration(email: String, password: String, confirmation: String): AuthIssue? = when {
        !email(email) -> AuthIssue.INVALID_EMAIL
        password.length < minimumPasswordLength || password.length > 128 -> AuthIssue.WEAK_PASSWORD
        password != confirmation -> AuthIssue.PASSWORD_MISMATCH
        else -> null
    }
    fun login(email: String, password: String): AuthIssue? = when {
        !email(email) -> AuthIssue.INVALID_EMAIL
        password.isEmpty() -> AuthIssue.INVALID_CREDENTIALS
        else -> null
    }
}
