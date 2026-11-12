package ge.gogichaishvili.backgammon.profile

import ge.gogichaishvili.backgammon.data.profile.*
import ge.gogichaishvili.backgammon.domain.profile.*
import ge.gogichaishvili.backgammon.domain.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class SupabaseProfileRepositoryTest {
    private val valid = """{"id":"40000000-0000-0000-0000-000000000001","username":"player_1","displayName":"გიორგი","avatarId":"crown","profileVersion":0,"statsVersion":0,"gamesPlayed":0,"gamesWon":0,"gamesLost":0,"rating":null,"createdAt":"2026-11-12T00:00:00Z"}"""
    private val draft=ProfileDraft(" Player_1 "," გიორგი ")
    private fun repo(status: Int=200,body: String=valid,auth: ProfileFakeAuth=ProfileFakeAuth(),
        inspect: (HttpRequestData)->Unit = {}): SupabaseProfileRepository {
        val http=HttpClient(MockEngine { request ->
            inspect(request);respond(body,HttpStatusCode.fromValue(status),headersOf(HttpHeaders.ContentType,"application/json"))
        })
        return SupabaseProfileRepository(http,"https://fixture.supabase.co","sb_publishable_fixture_not_real",auth) { "fixture-token-not-real" }
    }
    @Test fun loadsSafeProjection()=runTest {
        val answer=repo().loadMine() as ProfileResult.Found
        assertEquals("player_1",answer.profile.username);assertEquals(0L,answer.profile.gamesPlayed)
    }
    @Test fun requestUsesOwnBearerAndNoOwnerParameter()=runTest {
        var calls=0
        val repository=repo(inspect={ request ->
            calls++;assertEquals("Bearer fixture-token-not-real",request.headers[HttpHeaders.Authorization])
            assertEquals("/functions/v1/player-profile",request.url.encodedPath)
            assertFalse(request.url.toString().contains("fixture-owner"))
        })
        repository.create(draft);assertEquals(1,calls)
    }
    @Test fun missingIsDifferentFromUndeployedEndpoint()=runTest {
        assertEquals(ProfileResult.Missing,repo(404,"""{"code":"PROFILE_NOT_FOUND"}""").loadMine())
        assertEquals(ProfileResult.Failure(ProfileIssue.UNAVAILABLE),repo(404,"Not Found").loadMine())
    }
    @Test fun usernameConflictIsFriendly()=runTest {
        assertEquals(ProfileResult.Failure(ProfileIssue.USERNAME_UNAVAILABLE),
            repo(409,"""{"code":"USERNAME_UNAVAILABLE"}""").create(draft))
    }
    @Test fun versionConflictIsExplicit()=runTest {
        assertEquals(ProfileResult.Failure(ProfileIssue.VERSION_CONFLICT),repo(409,"""{"code":"VERSION_CONFLICT"}""")
            .update(draft,0,"30000000-0000-0000-0000-000000000001"))
    }
    @Test fun expiredSessionCannotReturnProfile()=runTest {
        assertEquals(ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED),repo(401).loadMine())
    }
    @Test fun signedOutNeverCallsNetwork()=runTest {
        var calls=0;val repository=repo(auth=ProfileFakeAuth(AuthState.SignedOut),inspect={calls++})
        assertEquals(ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED),repository.loadMine());assertEquals(0,calls)
    }
    @Test fun recoverySessionNeverCallsNetwork()=runTest {
        var calls=0;val repository=repo(auth=ProfileFakeAuth(AuthState.SignedIn(AuthUser("owner",null),true)),inspect={calls++})
        repository.loadMine();assertEquals(0,calls)
    }
    @Test fun accountSwitchFencesResponse()=runTest {
        val auth=ProfileFakeAuth()
        val repository=repo(auth=auth,inspect={auth.state.value=AuthState.SignedIn(AuthUser("other",null))})
        assertEquals(ProfileResult.Failure(ProfileIssue.AUTH_REQUIRED),repository.loadMine())
    }
    @Test fun serverErrorsNeverLeakRawDetails()=runTest {
        val result=repo(500,"sensitive database detail").loadMine()
        assertEquals(ProfileResult.Failure(ProfileIssue.UNAVAILABLE),result)
        assertFalse(result.toString().contains("sensitive"))
    }
    @Test fun networkIOExceptionIsRetryable()=runTest {
        val http=HttpClient(MockEngine { throw IOException("private failure") })
        val repository=SupabaseProfileRepository(http,"https://fixture.supabase.co","public",ProfileFakeAuth()){"token"}
        assertEquals(ProfileResult.Failure(ProfileIssue.NETWORK),repository.loadMine())
    }
    @Test fun rateLimitPreserved()=runTest {
        assertEquals(ProfileResult.Failure(ProfileIssue.RATE_LIMITED),repo(429).create(draft))
    }
    @Test fun unexpectedPrivateFieldsFailClosed()=runTest {
        val body=valid.dropLast(1)+""","email":"private@example.test"}"""
        assertEquals(ProfileResult.Failure(ProfileIssue.UNKNOWN),repo(body=body).loadMine())
    }
    @Test fun malformedCountersRejected()=runTest {
        assertEquals(ProfileResult.Failure(ProfileIssue.UNKNOWN),repo(body=valid.replace("\"gamesWon\":0","\"gamesWon\":1")).loadMine())
    }
    @Test fun futureAvatarFallsBackWithoutExternalFetch()=runTest {
        val result=repo(body=valid.replace("\"avatarId\":\"crown\"","\"avatarId\":\"future\"")).loadMine()
        assertEquals("crown",(result as ProfileResult.Found).profile.avatarId)
    }
    @Test fun invalidLocalInputAndVersionNeverSent()=runTest {
        var calls=0;val repository=repo(inspect={calls++})
        repository.create(ProfileDraft("a","Name"))
        repository.update(draft,-1,"bad")
        assertEquals(0,calls)
    }
    @Test fun malformedServerProfileRejected()=runTest {
        assertEquals(ProfileResult.Failure(ProfileIssue.UNKNOWN),repo(body=valid.replace("player_1","Admin")).loadMine())
    }
}
