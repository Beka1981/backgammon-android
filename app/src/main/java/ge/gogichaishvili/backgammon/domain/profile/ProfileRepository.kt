package ge.gogichaishvili.backgammon.domain.profile

import java.text.Normalizer

/** Public identity only: never Auth UUIDs, email, tokens or Storage URLs. */
data class PublicProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarId: String = BuiltInAvatars.defaultId,
    val version: Long = 0,
    val statsVersion: Long = 0,
    val gamesPlayed: Long = 0,
    val gamesWon: Long = 0,
    val gamesLost: Long = 0,
    val rating: Int? = null,
    val createdAt: String,
) {
    val winPercentage: Double get() = if (gamesPlayed == 0L) 0.0 else gamesWon.toDouble() / gamesPlayed * 100
}
object BuiltInAvatars {
    const val defaultId = "crown"
    val ids: List<String> = listOf("crown", "knight", "sun", "moon", "mountain", "lotus", "shield", "star")
    fun safe(id: String): String = id.takeIf { it in ids } ?: defaultId
}
data class ProfileDraft(val username: String = "", val displayName: String = "", val avatarId: String = BuiltInAvatars.defaultId)
enum class ProfileIssue { USERNAME_INVALID, USERNAME_RESERVED, USERNAME_UNAVAILABLE, DISPLAY_NAME_INVALID,
    AVATAR_INVALID, VERSION_CONFLICT, AUTH_REQUIRED, NETWORK, RATE_LIMITED, UNAVAILABLE, UNKNOWN }
sealed interface ProfileResult {
    data class Found(val profile: PublicProfile) : ProfileResult
    data object Missing : ProfileResult
    data class Failure(val issue: ProfileIssue) : ProfileResult
}
interface ProfileRepository {
    suspend fun loadMine(): ProfileResult
    /** Server derives owner from verified session; retry returns the existing profile. */
    suspend fun create(draft: ProfileDraft): ProfileResult
    suspend fun update(draft: ProfileDraft, expectedVersion: Long, commandId: String): ProfileResult
}
object ProfileValidation {
    val reserved = setOf("admin", "administrator", "support", "system", "moderator", "backgammon",
        "official", "supabase", "postgres", "api", "root", "null", "deleted")
    fun username(value: String): String = value.trim(' ').map {
        if (it in 'A'..'Z') it + 32 else it
    }.joinToString("")
    fun displayName(value: String): String = Normalizer.normalize(value.trim(' '), Normalizer.Form.NFC)
    fun normalized(draft: ProfileDraft) = draft.copy(username = username(draft.username), displayName = displayName(draft.displayName))
    fun validate(draft: ProfileDraft): ProfileIssue? {
        val value = normalized(draft)
        if (!Regex("[a-z0-9_]{3,20}").matches(value.username)) return ProfileIssue.USERNAME_INVALID
        if (value.username in reserved) return ProfileIssue.USERNAME_RESERVED
        val name = value.displayName
        val codePoints = name.codePoints().toArray()
        if (codePoints.size !in 1..40 || name.toByteArray(Charsets.UTF_8).size > 160 ||
            codePoints.any { it in 0..31 || it in 127..159 || it in 0xD800..0xDFFF ||
                it in 0x200B..0x200F || it in 0x2028..0x202E || it in 0x2060..0x206F || it == 0xFEFF })
            return ProfileIssue.DISPLAY_NAME_INVALID
        if (value.avatarId !in BuiltInAvatars.ids) return ProfileIssue.AVATAR_INVALID
        return null
    }
}
