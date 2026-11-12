package ge.gogichaishvili.backgammon.data.profile

import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.domain.profile.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.IOException

/** Allowlisted Edge API; no direct table access and no client-selected owner ID. */
class SupabaseProfileRepository(
    private val http: HttpClient,
    private val baseUrl: String,
    private val publishableKey: String,
    private val auth: AuthRepository,
    private val accessToken: () -> String?,
) : ProfileRepository {
    private val json = Json { ignoreUnknownKeys = false }
    override suspend fun loadMine() = request("GET")
    override suspend fun create(draft: ProfileDraft): ProfileResult {
        ProfileValidation.validate(draft)?.let { return ProfileResult.Failure(it) }
        return request("POST", ProfileValidation.normalized(draft))
    }
    override suspend fun update(draft: ProfileDraft, expectedVersion: Long, commandId: String): ProfileResult {
        ProfileValidation.validate(draft)?.let { return ProfileResult.Failure(it) }
        if (expectedVersion < 0 || !runCatching { java.util.UUID.fromString(commandId) }.isSuccess)
            return ProfileResult.Failure(ProfileIssue.UNKNOWN)
        return request("PATCH", ProfileValidation.normalized(draft), expectedVersion, commandId)
    }
    private suspend fun request(method: String, draft: ProfileDraft? = null, version: Long? = null,
        commandId: String? = null): ProfileResult {
        val owner = (auth.state.value as? AuthState.SignedIn)?.takeUnless { it.recoveryRequired }?.user?.id
            ?: return ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED)
        val token = accessToken()?.takeIf { it.isNotBlank() } ?: return ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED)
        return try {
            val response = http.request("$baseUrl/functions/v1/player-profile") {
                this.method = HttpMethod.parse(method)
                header("apikey", publishableKey)
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                if (draft != null) setBody(buildJsonObject {
                    put("username", draft.username); put("displayName", draft.displayName); put("avatarId", draft.avatarId)
                    version?.let { put("expectedVersion", it) }; commandId?.let { put("commandId", it) }
                }.toString())
            }
            // Logout/account switching fences late responses, even if a transport ignores cancellation.
            if ((auth.state.value as? AuthState.SignedIn)?.user?.id != owner)
                return ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED)
            val responseText = response.bodyAsChannel().readRemaining(16385L).readByteArray()
            if (responseText.size > 16384) return ProfileResult.Failure(ProfileIssue.UNKNOWN)
            val body = responseText.decodeToString(throwOnInvalidSequence = true)
            when (response.status.value) {
                200, 201 -> {
                    val wire = json.decodeFromString<ProfileWire>(body)
                    ProfileResult.Found(wire.toDomain())
                }
                404 -> if (runCatching { json.decodeFromString<ErrorWire>(body).code }.getOrNull() == "PROFILE_NOT_FOUND") ProfileResult.Missing
                    else ProfileResult.Failure(ProfileIssue.UNAVAILABLE)
                401, 403 -> ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED)
                429 -> ProfileResult.Failure(ProfileIssue.RATE_LIMITED)
                409 -> {
                    val error = runCatching { json.decodeFromString<ErrorWire>(body).code }.getOrNull()
                    ProfileResult.Failure(if (error == "USERNAME_UNAVAILABLE") ProfileIssue.USERNAME_UNAVAILABLE
                        else ProfileIssue.VERSION_CONFLICT)
                }
                400 -> {
                    val code = runCatching { json.decodeFromString<ErrorWire>(body).code }.getOrNull()
                    ProfileResult.Failure(when (code) {
                        "USERNAME_INVALID" -> ProfileIssue.USERNAME_INVALID
                        "USERNAME_RESERVED" -> ProfileIssue.USERNAME_RESERVED
                        "DISPLAY_NAME_INVALID" -> ProfileIssue.DISPLAY_NAME_INVALID
                        "AVATAR_INVALID" -> ProfileIssue.AVATAR_INVALID
                        else -> ProfileIssue.UNKNOWN
                    })
                }
                else -> ProfileResult.Failure(ProfileIssue.UNAVAILABLE)
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: IOException) { ProfileResult.Failure(ProfileIssue.NETWORK) }
        catch (_: io.ktor.client.plugins.HttpRequestTimeoutException) { ProfileResult.Failure(ProfileIssue.NETWORK) }
        catch (_: Exception) { ProfileResult.Failure(ProfileIssue.UNKNOWN) }
    }
}
@Serializable private data class ErrorWire(val code: String)
@Serializable private data class ProfileWire(
    val id: String, val username: String, val displayName: String, val avatarId: String,
    val profileVersion: Long, val statsVersion: Long, val gamesPlayed: Long, val gamesWon: Long,
    val gamesLost: Long, val rating: Int? = null, val createdAt: String,
) {
    fun toDomain(): PublicProfile {
        require(java.util.UUID.fromString(id).toString() == id)
        require(profileVersion >= 0 && statsVersion >= 0 && gamesPlayed >= 0 && gamesWon >= 0 && gamesLost >= 0)
        require(gamesWon <= gamesPlayed && gamesLost == gamesPlayed - gamesWon)
        require(ProfileValidation.validate(ProfileDraft(username, displayName, BuiltInAvatars.safe(avatarId))) == null)
        require(ProfileValidation.username(username) == username && ProfileValidation.displayName(displayName) == displayName)
        require(java.time.Instant.parse(createdAt).toEpochMilli() >= 0)
        return PublicProfile(id, username, displayName, BuiltInAvatars.safe(avatarId), profileVersion,
            statsVersion, gamesPlayed, gamesWon, gamesLost, rating, createdAt)
    }
}
class UnconfiguredProfileRepository : ProfileRepository {
    override suspend fun loadMine() = ProfileResult.Failure(ProfileIssue.UNAVAILABLE)
    override suspend fun create(draft: ProfileDraft) = loadMine()
    override suspend fun update(draft: ProfileDraft, expectedVersion: Long, commandId: String) = loadMine()
}
