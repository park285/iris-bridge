package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LazyKakaoProfileDetailFetcherTest {
    @Test
    fun `fetchProfileDetail retries discovery until available and caches success`() {
        var discoveryCalls = 0
        val fetcher =
            LazyKakaoProfileDetailFetcher {
                discoveryCalls++
                if (discoveryCalls == 1) {
                    null
                } else {
                    ProfileDetailUpstream { _, profile ->
                        UpstreamProfileDetail(profileImageUrl = "https://example.test/${profile.userId}.gif")
                    }
                }
            }

        assertNull(fetcher.fetchProfileDetail(55L, profile(57L)))
        assertEquals("https://example.test/57.gif", fetcher.fetchProfileDetail(55L, profile(57L))?.profileImageUrl)
        assertEquals("https://example.test/58.gif", fetcher.fetchProfileDetail(55L, profile(58L))?.profileImageUrl)
        assertEquals(2, discoveryCalls)
    }

    private fun profile(userId: Long): UpstreamMemberProfile =
        UpstreamMemberProfile(
            userId = userId,
            nickName = "Member $userId",
            profileImageUrl = "https://example.test/static-$userId.jpg",
            accessPermit = "permit-$userId",
        )
}
