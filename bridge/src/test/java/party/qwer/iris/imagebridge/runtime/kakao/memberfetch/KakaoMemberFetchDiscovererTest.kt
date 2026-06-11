@file:Suppress("ClassName", "FunctionName", "UNUSED_PARAMETER")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KakaoMemberFetchDiscovererTest {
    @Test
    fun `member fetch matcher accepts latest suspend member API singleton`() {
        assertTrue(matchesMemberFetchFacadeForTest(FakeLatestMemberFetchClient::class.java))
    }

    @Test
    fun `member fetch matcher accepts Kakao 26_4_2 LocoImpl style member API`() {
        assertTrue(matchesMemberFetchFacadeForTest(FakeKakao2642LocoImplLikeClient::class.java))
    }

    @Test
    fun `member fetch discovery prefers latest requested member API over add-member fallback`() {
        val method = findFetchMembersMethodForTest(FakeLatestMemberFetchClient::class.java)

        assertEquals("Y", method?.name)
    }

    @Test
    fun `member fetch discovery uses room member API instead of add-member fallback`() {
        val method = findFetchMembersMethodForTest(FakeRoomOnlyMemberFetchClient::class.java)

        assertEquals("D", method?.name)
    }

    @Test
    fun `member fetch matcher rejects chat room manager false positive`() {
        assertFalse(matchesMemberFetchFacadeForTest(FakeChatRoomListManagerLikeClient::class.java))
    }
}

internal class FakeLatestMemberFetchClient {
    companion object {
        @JvmField
        val b: FakeLatestMemberFetchClient = FakeLatestMemberFetchClient()
    }

    suspend fun Y(
        chatId: Long,
        memberIds: List<Long>,
    ): FakeLocoResult =
        FakeLocoResult(
            FakeMemberResponse(
                members =
                    memberIds.map { memberId -> FakeMember(memberId, "Member $memberId") } +
                        FakeMember(chatId + 9_000L, "Unrequested Member"),
            ),
        )

    fun i(
        chatId: Long,
        memberIds: List<Long>,
    ): FakeLocoResult =
        FakeLocoResult(
            FakeMemberResponse(
                members =
                    memberIds.map { memberId -> FakeMember(memberId, "Member $memberId") } +
                        FakeMember(chatId + 9_000L, "Unrequested Member"),
            ),
        )

    suspend fun D(
        chatId: Long,
    ): FakeLocoResult =
        FakeLocoResult(
            FakeMemberResponse(
                members =
                    listOf(
                        FakeMember(chatId + 1L, "Member ${chatId + 1L}"),
                        FakeMember(chatId + 2L, "Member ${chatId + 2L}"),
                    ),
            ),
        )
}

internal class FakeChatRoomListManagerLikeClient {
    companion object {
        @JvmField
        val q: FakeChatRoomListManagerLikeClient = FakeChatRoomListManagerLikeClient()
    }

    fun e0(
        chatId: Long,
        includePreChatRoom: Boolean,
        includeSoftDeleted: Boolean,
    ): FakeChatRoom = FakeChatRoom(chatId)

    fun i(
        chatId: Long,
        memberIds: List<Long>,
    ): FakeChatRoom = FakeChatRoom(chatId + memberIds.size)
}

internal class FakeRoomOnlyMemberFetchClient {
    companion object {
        @JvmField
        val b: FakeRoomOnlyMemberFetchClient = FakeRoomOnlyMemberFetchClient()
    }

    fun i(
        chatId: Long,
        memberIds: List<Long>,
    ): FakeLocoResult = FakeLocoResult(FakeMemberResponse(memberIds.map { memberId -> FakeMember(memberId, "Member $memberId") }))

    suspend fun D(
        chatId: Long,
    ): FakeLocoResult =
        FakeLocoResult(
            FakeMemberResponse(
                members =
                    listOf(
                        FakeMember(chatId + 1L, "Member ${chatId + 1L}"),
                    ),
            ),
        )
}

internal class FakeKakao2642LocoImplLikeClient {
    companion object {
        @JvmField
        val b: FakeKakao2642LocoImplLikeClient = FakeKakao2642LocoImplLikeClient()
    }

    suspend fun S0(
        chatId: Long,
        memberIds: List<Long>,
    ): FakeLocoResult =
        FakeLocoResult(
            FakeMemberResponse(
                members =
                    memberIds.map { memberId -> FakeMember(memberId, "Member $memberId") },
            ),
        )
}

internal data class FakeChatRoom(
    val chatId: Long,
)
