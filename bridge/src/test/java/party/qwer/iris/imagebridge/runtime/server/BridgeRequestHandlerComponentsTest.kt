package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile
import party.qwer.iris.imagebridge.runtime.kakao.userdb.FakeUserModel
import party.qwer.iris.imagebridge.runtime.kakao.userdb.KakaoUserDatabaseReader
import party.qwer.iris.imagebridge.runtime.kakao.userdb.buildFakeUserDbAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BridgeRequestHandlerComponentsTest {
    @Test
    fun `member profile fetcher keeps upstream when userdb is unavailable`() {
        val upstream =
            MemberProfileUpstream { _, userIds ->
                userIds.associateWith { userId ->
                    UpstreamMemberProfile(userId, "Upstream Member $userId", null)
                }
            }

        val fetcher = buildMemberProfileFetcherForTest(upstream, userDbReader = null)

        assertSame(upstream, fetcher)
        assertEquals("Upstream Member 42", fetcher?.fetchMemberProfiles(1L, listOf(42L))?.get(42L)?.nickName)
    }

    @Test
    fun `member profile fetcher lets userdb cache override upstream when available`() {
        val upstream =
            MemberProfileUpstream { _, userIds ->
                userIds.associateWith { userId ->
                    UpstreamMemberProfile(userId, "Upstream Member $userId", "https://example.invalid/$userId")
                }
            }
        val access =
            buildFakeUserDbAccess { userId ->
                if (userId == 42L) FakeUserModel(userId, "Cached Member 42") else null
            }
        val reader = KakaoUserDatabaseReader(access)

        val fetcher = buildMemberProfileFetcherForTest(upstream, reader)
        val result = fetcher?.fetchMemberProfiles(1L, listOf(42L, 77L)).orEmpty()

        assertEquals("Cached Member 42", result[42L]?.nickName)
        assertEquals("https://example.invalid/42", result[42L]?.profileImageUrl)
        assertEquals("Upstream Member 77", result[77L]?.nickName)
    }

    @Test
    fun `member profile fetcher does not serve userdb cache when upstream discovery is unavailable`() {
        val access =
            buildFakeUserDbAccess { userId ->
                if (userId == 42L) FakeUserModel(userId, "Cached Member 42") else null
            }
        val reader = KakaoUserDatabaseReader(access)

        val fetcher = buildMemberProfileFetcherForTest(baseFetcher = null, userDbReader = reader)

        assertNull(fetcher)
    }
}
