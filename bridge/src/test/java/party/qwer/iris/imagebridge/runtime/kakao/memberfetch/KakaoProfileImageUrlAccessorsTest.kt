package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import kotlin.test.Test
import kotlin.test.assertEquals

class KakaoProfileImageUrlAccessorsTest {
    @Test
    fun `profileDetailImageUrl prefers original animated URL over static originals`() {
        val detail =
            FakeProfileDetail(
                FakeProfileImage(
                    originalAnimatedUrl = "https://example.test/profile-original.gif",
                    mediumAnimatedUrl = "https://example.test/profile-medium.gif",
                    originalUrl = "https://example.test/profile-original.jpg",
                    mediumUrl = "https://example.test/profile-medium.jpg",
                    thumbnailUrl = "https://example.test/profile-thumb.jpg",
                ),
            )

        assertEquals("https://example.test/profile-original.gif", profileDetailImageUrl(detail))
    }

    @Test
    fun `profileDetailImageUrl falls back through animated and static profile URLs`() {
        assertEquals(
            "https://example.test/profile-medium.gif",
            profileDetailImageUrl(
                FakeProfileDetail(
                    FakeProfileImage(
                        originalAnimatedUrl = null,
                        mediumAnimatedUrl = "https://example.test/profile-medium.gif",
                    ),
                ),
            ),
        )
        assertEquals(
            "https://example.test/profile-original.jpg",
            profileDetailImageUrl(
                FakeProfileDetail(
                    FakeProfileImage(
                        originalAnimatedUrl = null,
                        mediumAnimatedUrl = null,
                        originalUrl = "https://example.test/profile-original.jpg",
                    ),
                ),
            ),
        )
    }
}

private class FakeProfileDetail(
    private val profileImage: FakeProfileImage,
) {
    fun getProfileImage(): FakeProfileImage = profileImage

    fun v(): FakeProfileImage = profileImage
}

private class FakeProfileImage(
    private val originalAnimatedUrl: String? = null,
    private val mediumAnimatedUrl: String? = null,
    private val originalUrl: String? = null,
    private val mediumUrl: String? = null,
    private val thumbnailUrl: String? = null,
) {
    fun getOriginalAnimatedUrl(): String? = originalAnimatedUrl

    fun getMediumAnimatedUrl(): String? = mediumAnimatedUrl

    fun getOriginalUrl(): String? = originalUrl

    fun getMediumUrl(): String? = mediumUrl

    fun getThumbnailUrl(): String? = thumbnailUrl

    fun d(): String? = originalAnimatedUrl

    fun a(): String? = mediumAnimatedUrl

    fun e(): String? = originalUrl

    fun b(): String? = mediumUrl

    fun f(): String? = thumbnailUrl
}
