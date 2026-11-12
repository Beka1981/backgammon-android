package ge.gogichaishvili.backgammon.profile

import ge.gogichaishvili.backgammon.domain.profile.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ProfileValidationTest(private val draft: ProfileDraft, private val issue: ProfileIssue?) {
    @Test fun validatesSharedBoundary() { assertEquals(issue, ProfileValidation.validate(draft)) }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {1}") fun cases(): List<Array<Any?>> = listOf(
            ProfileDraft("abc","Player") to null,
            ProfileDraft(" A_B123 "," გიორგი ") to null,
            ProfileDraft("a".repeat(20),"🙂".repeat(40)) to null,
            ProfileDraft("ab","Player") to ProfileIssue.USERNAME_INVALID,
            ProfileDraft("a".repeat(21),"Player") to ProfileIssue.USERNAME_INVALID,
            ProfileDraft("აბგ","Player") to ProfileIssue.USERNAME_INVALID,
            ProfileDraft("аbc","Player") to ProfileIssue.USERNAME_INVALID,
            ProfileDraft("user-name","Player") to ProfileIssue.USERNAME_INVALID,
            ProfileDraft("ＡＢＣ","Player") to ProfileIssue.USERNAME_INVALID,
            ProfileDraft("Admin","Player") to ProfileIssue.USERNAME_RESERVED,
            ProfileDraft("SUPPORT","Player") to ProfileIssue.USERNAME_RESERVED,
            ProfileDraft("valid","") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid"," ".repeat(8)) to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","x".repeat(41)) to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","Name\nOther") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","\u0000") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","A\u0085B") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","A\u202EB") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","A\u200BB") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","\uD800") to ProfileIssue.DISPLAY_NAME_INVALID,
            ProfileDraft("valid","Player","https://evil.invalid/avatar") to ProfileIssue.AVATAR_INVALID,
            ProfileDraft("valid","e\u0301") to null,
            ProfileDraft("valid","გიორგი") to null,
        ).map { arrayOf(it.first,it.second) }
    }
}
class ProfileModelTest {
    @Test fun nfcAndAsciiCanonicalization() {
        assertEquals(ProfileDraft("player_1","é"),ProfileValidation.normalized(ProfileDraft(" Player_1 "," e\u0301 ")))
    }
    @Test fun avatarsHaveEightStableUniqueIdsAndSafeFallback() {
        assertEquals(8,BuiltInAvatars.ids.toSet().size)
        assertEquals("crown",BuiltInAvatars.safe("future"))
    }
    @Test fun percentageIsDerivedIncludingZero() {
        val profile = profileFixture()
        assertEquals(0.0,profile.winPercentage,0.001)
        assertEquals(75.0,profile.copy(gamesPlayed=4,gamesWon=3,gamesLost=1).winPercentage,0.001)
    }
}
