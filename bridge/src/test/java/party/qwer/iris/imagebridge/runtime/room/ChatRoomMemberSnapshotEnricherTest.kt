package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRoomMemberSnapshotEnricherTest {
    @Test
    fun `enrich replaces reflection artifact nickname from upstream fetch`() {
        val fetcher =
            MemberProfileUpstream { _, userIds ->
                userIds.associateWith { userId ->
                    UpstreamMemberProfile(userId, "국헌", null)
                }
            }
        val enricher = ChatRoomMemberSnapshotEnricher(fetcher)
        val snapshot =
            ImageBridgeProtocol.ChatRoomMembersSnapshot(
                roomId = 55L,
                scannedAtEpochMs = 1L,
                members =
                    listOf(
                        ImageBridgeProtocol.ChatRoomMemberSnapshot(
                            userId = 243_338_321L,
                            nickname = "creatorUserId",
                        ),
                    ),
            )

        val enriched =
            enricher.enrich(
                snapshot,
                listOf(ImageBridgeProtocol.ChatRoomMemberHint(userId = 243_338_321L)),
            )

        assertEquals("국헌", enriched.members.single().nickname)
    }

    @Test
    fun `enrich fills blank nickname from upstream fetch`() {
        val fetcher =
            MemberProfileUpstream { _, userIds ->
                userIds.associateWith { userId ->
                    UpstreamMemberProfile(userId, "Alice", "https://example.com/a.png")
                }
            }
        val enricher = ChatRoomMemberSnapshotEnricher(fetcher)
        val snapshot =
            ImageBridgeProtocol.ChatRoomMembersSnapshot(
                roomId = 55L,
                scannedAtEpochMs = 1L,
                members =
                    listOf(
                        ImageBridgeProtocol.ChatRoomMemberSnapshot(userId = 7L, nickname = ""),
                    ),
            )

        val enriched =
            enricher.enrich(
                snapshot,
                listOf(ImageBridgeProtocol.ChatRoomMemberHint(userId = 7L)),
            )

        assertEquals("Alice", enriched.members.single().nickname)
        assertEquals("https://example.com/a.png", enriched.members.single().profileImageUrl)
        assertTrue(enriched.sourcePath.orEmpty().contains("upstream:member"))
    }
}
