package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import kotlin.test.Test
import kotlin.test.assertEquals

class KakaoMemberModelAccessorsTest {
    @Test
    fun `memberObjectToProfile prefers original profile URL over full and thumbnail URL`() {
        val profile = memberObjectToProfile(ProfileUrlMember())

        assertEquals("https://example.test/member-57-original.gif", profile?.profileImageUrl)
    }

    @Test
    fun `memberObjectToProfile preserves access permit for profile detail fetch`() {
        val profile = memberObjectToProfile(ProfileUrlMember())

        assertEquals("access-permit-57", profile?.accessPermit)
    }
}

private class ProfileUrlMember {
    fun n(): Long = 57L

    fun f(): String = "Member 57"

    fun getProfileUrl(): String = "https://example.test/member-57-thumb.png"

    fun getFullProfileUrl(): String = "https://example.test/member-57-full.png"

    fun getOriginalProfileUrl(): String = "https://example.test/member-57-original.gif"

    fun getAccessPermit(): String = "access-permit-57"

    fun j(): String = "https://example.test/member-57-thumb.png"

    fun d(): String = "https://example.test/member-57-full.png"

    fun g(): String = "https://example.test/member-57-original.gif"

    fun a(): String = "access-permit-57"
}
