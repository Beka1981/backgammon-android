package ge.gogichaishvili.backgammon.data.auth

import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** SDK storage seam: sessions, verifier and recovery flags share an encrypted, backup-excluded file. */
interface AuthStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String?)
    suspend fun clear()
}
class AuthStorageException : IOException("Secure authentication storage unavailable")
internal class StoredSessionManager(private val storage: AuthStorage) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun saveSession(session: UserSession) = storage.write("session", json.encodeToString(session))
    override suspend fun loadSession(): UserSession? = storage.read("session")?.let {
        try { json.decodeFromString<UserSession>(it) } catch (_: Exception) {
            storage.clear()
            throw AuthStorageException()
        }
    }
    override suspend fun deleteSession() = storage.write("session", null)
}
internal class StoredVerifier(private val storage: AuthStorage) : CodeVerifierCache {
    override suspend fun saveCodeVerifier(codeVerifier: String) = storage.write("verifier", codeVerifier)
    override suspend fun loadCodeVerifier() = storage.read("verifier")
    override suspend fun deleteCodeVerifier() = storage.write("verifier", null)
}
