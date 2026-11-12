package ge.gogichaishvili.backgammon.data.auth

import ge.gogichaishvili.backgammon.BuildConfig
import ge.gogichaishvili.backgammon.domain.auth.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URI

class SupabaseConfiguration private constructor(val url: String, val publishableKey: String) {
    override fun toString() = "SupabaseConfiguration([redacted])"
    companion object {
        fun parse(url: String, key: String): SupabaseConfiguration? = runCatching {
            val uri = URI(url.trim())
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.port !in listOf(-1, 443) || uri.path !in listOf("", "/") ||
                uri.rawQuery != null || uri.rawFragment != null ||
                !key.trim().matches(Regex("sb_publishable_[A-Za-z0-9_-]{12,490}"))) return null
            SupabaseConfiguration(url.trim().trimEnd('/'), key.trim())
        }.getOrNull()
    }
}
data class AccountServices(val auth: AuthRepository, val profiles: ge.gogichaishvili.backgammon.domain.profile.ProfileRepository)
object SupabaseClientProvider {
    fun repository(storage: AuthStorage, scope: CoroutineScope): AuthRepository = services(storage, scope).auth
    fun services(storage: AuthStorage, scope: CoroutineScope): AccountServices {
        val config = SupabaseConfiguration.parse(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        if (!BuildConfig.SUPABASE_CONFIGURED || config == null) return AccountServices(UnconfiguredAuthRepository(),
            ge.gogichaishvili.backgammon.data.profile.UnconfiguredProfileRepository())
        val client = createSupabaseClient(config.url, config.publishableKey) {
            httpEngine = OkHttp.create()
            defaultLogLevel = LogLevel.NONE
            install(Auth) {
                flowType = FlowType.PKCE
                autoLoadFromStorage = false // Repository catches storage/refresh failures without blocking Local Play.
                sessionManager = StoredSessionManager(storage)
                codeVerifierCache = StoredVerifier(storage)
            }
        }
        val authRepository = SupabaseAuthRepository(client, storage, scope, config.url, BuildConfig.APPLICATION_ID)
        val profileHttp = io.ktor.client.HttpClient(OkHttp) {
            install(io.ktor.client.plugins.HttpTimeout) { requestTimeoutMillis = 15000; connectTimeoutMillis = 10000 }
        }
        val profiles = ge.gogichaishvili.backgammon.data.profile.SupabaseProfileRepository(
            profileHttp, config.url, config.publishableKey, authRepository) {
            client.auth.currentSessionOrNull()?.accessToken
        }
        return AccountServices(authRepository, profiles)
    }
}
class UnconfiguredAuthRepository : AuthRepository {
    override val state = MutableStateFlow<AuthState>(AuthState.Unconfigured)
    private fun failure() = AuthResult.Failure(AuthIssue.UNCONFIGURED)
    override suspend fun login(email: String, password: String) = failure()
    override suspend fun register(email: String, password: String) = failure()
    override suspend fun logout() = failure()
    override suspend fun refresh() = failure()
    override suspend fun requestPasswordReset(email: String) = failure()
    override suspend fun updatePassword(password: String) = failure()
    override suspend fun handleCallback(url: String) = failure()
}
