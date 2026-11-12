package ge.gogichaishvili.backgammon.auth

import ge.gogichaishvili.backgammon.data.auth.*
import ge.gogichaishvili.backgammon.domain.auth.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

class SupabaseAuthRepositoryTest {
    private class Memory : AuthStorage {
        val values = mutableMapOf<String, String>()
        var failRead: String? = null
        override suspend fun read(key: String): String? {
            if (key == failRead) throw AuthStorageException()
            return values[key]
        }
        override suspend fun write(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override suspend fun clear() { values.clear() }
    }
    private suspend fun fixture(
        storage: Memory = Memory(),
        handler: MockRequestHandler,
        afterReady: suspend (io.github.jan.supabase.SupabaseClient) -> Unit = {},
        check: suspend (SupabaseAuthRepository, Memory) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val client = createSupabaseClient("https://fixture.supabase.co", "sb_publishable_fixture_key_not_real") {
            defaultLogLevel = LogLevel.NONE
            httpEngine = MockEngine(handler)
            install(Auth) {
                flowType = FlowType.PKCE
                autoLoadFromStorage = false
                enableLifecycleCallbacks = false
                sessionManager = StoredSessionManager(storage)
                codeVerifierCache = StoredVerifier(storage)
            }
        }
        try {
            val repo = SupabaseAuthRepository(client, storage, scope, "fixture-project")
            withTimeout(10000) { repo.state.first { it != AuthState.Loading } }
            afterReady(client)
            check(repo, storage)
        } finally { scope.cancel(); client.close() }
    }
    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val session = """{"access_token":"fixture-access-not-real","refresh_token":"fixture-refresh-not-real","token_type":"bearer","expires_in":3600,"user":{"id":"00000000-0000-0000-0000-000000000001","aud":"authenticated","email":"fixture@example.test","role":"authenticated"}}"""

    @Test fun loginUsesServerIdentityAndPersistsSession() = runBlocking {
        fixture(handler = { respond(session, HttpStatusCode.OK, headers) }) { repo, storage ->
            assertEquals(AuthResult.Success, repo.login("fixture@example.test", "fixture-password"))
            assertEquals("00000000-0000-0000-0000-000000000001", (repo.state.value as AuthState.SignedIn).user.id)
            assertNotNull(storage.read("session"))
        }
    }
    @Test fun invalidCredentialsAreMappedWithoutRawServerMessage() = runBlocking {
        fixture(handler = { respond("""{"error_code":"invalid_credentials","msg":"private server details"}""", HttpStatusCode.BadRequest, headers) }) { repo, _ ->
            assertEquals(AuthResult.Failure(AuthIssue.INVALID_CREDENTIALS), repo.login("fixture@example.test", "wrong"))
            assertEquals(AuthState.SignedOut, repo.state.value)
        }
    }
    @Test fun existingRegistrationHasGenericConfirmation() = runBlocking {
        fixture(handler = { respond("""{"error_code":"user_already_exists","msg":"private"}""", HttpStatusCode.BadRequest, headers) }) { repo, _ ->
            assertEquals(AuthResult.ConfirmationSent, repo.register("fixture@example.test", "Fixture password 123!"))
        }
    }
    @Test fun corruptStoredSessionFailsClosed() = runBlocking {
        val storage = Memory().apply { values["project"] = "fixture-project"; values["session"] = "corrupted" }
        fixture(storage, handler = { error("Corrupt sessions must never reach the network") }) { repo, saved ->
            assertEquals(AuthState.RefreshFailed(null, AuthIssue.STORAGE), repo.state.value)
            assertNull(saved.read("session"))
        }
    }
    @Test fun changingProjectClearsOldSessionBeforeAnyNetworkRequest() = runBlocking {
        val storage = Memory().apply { values["project"] = "other-project"; values["session"] = session }
        fixture(storage, handler = { error("Old credentials must not be sent to another project") }) { repo, saved ->
            assertEquals(AuthState.SignedOut, repo.state.value)
            assertNull(saved.read("session"))
        }
    }
    @Test fun callbackWithoutVerifierMakesNoNetworkRequest() = runBlocking {
        fixture(handler = { error("Invalid callback must not reach network") }) { repo, _ ->
            assertEquals(AuthResult.Failure(AuthIssue.INVALID_LINK),
                repo.handleCallback("ge.gogichaishvili.backgammon://auth/recovery?code=fixture"))
        }
    }
    @Test fun persistedSessionRefreshesBeforeBecomingSignedIn() = runBlocking {
        val storage = Memory().apply { values["project"] = "fixture-project"; values["session"] = session }
        var requests = 0
        fixture(storage, handler = { requests++; respond(session, HttpStatusCode.OK, headers) }) { repo, _ ->
            assertTrue(repo.state.value is AuthState.SignedIn)
            assertEquals(1, requests)
        }
    }
    @Test fun offlineRestorationDoesNotPublishCachedSessionAsAuthenticated() = runBlocking {
        val storage = Memory().apply { values["project"] = "fixture-project"; values["session"] = session }
        fixture(storage, handler = { throw java.io.IOException("fixture offline") }) { repo, _ ->
            assertTrue(repo.state.value is AuthState.RefreshFailed)
        }
    }
    @Test fun recoveryRequirementSurvivesProcessRecreation() = runBlocking {
        val storage = Memory().apply {
            values["project"] = "fixture-project"; values["session"] = session; values["recovery"] = "true"
        }
        fixture(storage, handler = { respond(session, HttpStatusCode.OK, headers) }) { repo, _ ->
            assertTrue((repo.state.value as AuthState.SignedIn).recoveryRequired)
        }
    }
    @Test fun offlineLogoutClearsSessionVerifierAndRecovery() = runBlocking {
        fixture(handler = {
            if (it.url.encodedPath.endsWith("/logout")) throw java.io.IOException("fixture offline")
            respond(session, HttpStatusCode.OK, headers)
        }) { repo, storage ->
            repo.login("fixture@example.test", "fixture-password")
            storage.write("verifier", "fixture"); storage.write("recovery", "true")
            repo.logout()
            assertEquals(AuthState.SignedOut, repo.state.value)
            assertNull(storage.read("session")); assertNull(storage.read("verifier")); assertNull(storage.read("recovery"))
        }
    }
    @Test fun recoveryCallbackPersistsGateAndRequiresPasswordChange() = runBlocking {
        var exchanges = 0
        fixture(handler = {
            when {
                it.url.encodedPath.endsWith("/recover") -> respond("{}", HttpStatusCode.OK, headers)
                it.url.encodedPath.endsWith("/user") -> respond(
                    """{"id":"00000000-0000-0000-0000-000000000001","aud":"authenticated","email":"fixture@example.test"}""",
                    HttpStatusCode.OK, headers)
                else -> { exchanges++; respond(session, HttpStatusCode.OK, headers) }
            }
        }) { repo, storage ->
            repo.requestPasswordReset("fixture@example.test")
            withTimeout(5000) { while (storage.read("verifier") == null) delay(10) }
            val link = "ge.gogichaishvili.backgammon://auth/recovery?code=fixture-code"
            assertEquals(AuthResult.Success, repo.handleCallback(link))
            assertTrue((repo.state.value as AuthState.SignedIn).recoveryRequired)
            assertEquals("true", storage.read("recovery"))
            assertEquals(AuthResult.Success, repo.handleCallback(link))
            assertEquals(1, exchanges)
            assertEquals(AuthResult.Success, repo.updatePassword("New fixture password 123!"))
            assertFalse((repo.state.value as AuthState.SignedIn).recoveryRequired)
            assertNull(storage.read("recovery"))
        }
    }
    @Test fun resetPasswordWithoutRecoverySessionIsRejectedLocally() = runBlocking {
        fixture(handler = { error("No network expected") }) { repo, _ ->
            assertEquals(AuthResult.Failure(AuthIssue.INVALID_LINK), repo.updatePassword("New fixture password 123!"))
        }
    }
    @Test fun backgroundSessionStorageFailureLocksAuthenticationInsteadOfKillingObserver() = runBlocking {
        val saved = Memory()
        fixture(saved, handler = { error("No network expected") }, afterReady = { client ->
            saved.failRead = "recovery"
            client.auth.importSession(
                kotlinx.serialization.json.Json.decodeFromString<io.github.jan.supabase.auth.user.UserSession>(session),
                autoRefresh = false)
        }) { repo, _ ->
            val failed = withTimeout(5000) { repo.state.first { it is AuthState.RefreshFailed } }
            assertEquals(AuthState.RefreshFailed(null, AuthIssue.STORAGE), failed)
            assertEquals(AuthResult.Failure(AuthIssue.STORAGE), repo.login("fixture@example.test", "fixture-password"))
        }
    }
    @Test fun passwordResetUsesConfiguredRecoveryRedirect() = runBlocking {
        fixture(handler = {
            assertTrue(it.url.toString().contains("recovery"))
            respond("{}", HttpStatusCode.OK, headers)
        }) { repo, _ ->
            assertEquals(AuthResult.ConfirmationSent, repo.requestPasswordReset("fixture@example.test"))
        }
    }
}
