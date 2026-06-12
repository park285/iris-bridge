@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class KakaoProfileDetailFetcherTest {
    @Test
    fun `fetchProfileDetail prefers refresh endpoint when available`() {
        val api = FakeProfileDetailApi()
        val fetcher =
            KakaoProfileDetailFetcher(
                KakaoProfileDetailAccess(
                    profileApi = api,
                    otherProfileMethod = FakeProfileDetailApi::class.java.methods.single { it.name == "other" },
                    refreshOtherProfileMethod = FakeProfileDetailApi::class.java.methods.single { it.name == "refresh" },
                ),
            )

        val executor = Executors.newSingleThreadExecutor()
        val detail =
            try {
                executor
                    .submit<UpstreamProfileDetail?> {
                        fetcher.fetchProfileDetail(
                            chatId = 55L,
                            profile =
                                UpstreamMemberProfile(
                                    userId = 57L,
                                    nickName = "Member 57",
                                    profileImageUrl = "https://example.test/static.jpg",
                                    accessPermit = "permit-57",
                                ),
                        )
                    }.get()
            } finally {
                executor.shutdownNow()
            }

        assertEquals("https://example.test/refresh-original.gif", detail?.profileImageUrl)
        assertEquals(1, api.refreshCalls)
        assertEquals(0, api.otherCalls)
    }
}

private class FakeProfileDetailApi {
    var refreshCalls = 0
    var otherCalls = 0

    fun refresh(
        userId: Long,
        accessPermit: String,
        chatId: java.lang.Long,
        continuation: Continuation<Any?>,
    ): Any {
        refreshCalls++
        return FakeRefreshProfileDetail(FakeRefreshProfileImage(originalAnimatedUrl = "https://example.test/refresh-original.gif"))
    }

    fun other(
        userId: Long,
        accessPermit: String,
        chatId: java.lang.Long,
        continuation: Continuation<Any?>,
    ): Any {
        otherCalls++
        return FakeRefreshProfileDetail(FakeRefreshProfileImage(originalUrl = "https://example.test/other-original.jpg"))
    }
}

private class FakeRefreshProfileDetail(
    private val profileImage: FakeRefreshProfileImage,
) {
    fun getProfileImage(): FakeRefreshProfileImage = profileImage
}

private class FakeRefreshProfileImage(
    private val originalAnimatedUrl: String? = null,
    private val originalUrl: String? = null,
) {
    fun getOriginalAnimatedUrl(): String? = originalAnimatedUrl

    fun getOriginalUrl(): String? = originalUrl
}
