package party.qwer.iris

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageBridgeProtocolTest {
    @Test
    fun `handshake hello omits bridge token and payload fields`() {
        val buffer = ByteArrayOutputStream()
        val hello =
            ImageBridgeHandshakeProtocol.buildHello(
                clientNonce = "client-nonce",
                socketName = "iris-image-bridge-mux",
                timestampMs = 1234L,
            )

        ImageBridgeHandshakeProtocol.writeFrame(buffer, hello)

        val payload = LengthPrefixedFrameCodec.readPayload(ByteArrayInputStream(buffer.toByteArray()))
        assertTrue(payload.contains(""""type":"hello""""))
        assertTrue(payload.contains(""""clientNonce":"client-nonce""""))
        assertFalse(payload.contains("token"))
        assertFalse(payload.contains("message"))
    }

    @Test
    fun `handshake proof uses separate server and client domains`() {
        val serverProof =
            ImageBridgeHandshakeProtocol.serverProof(
                bridgeToken = "bridge-token",
                clientNonce = "client-nonce",
                serverNonce = "server-nonce",
                socketName = "iris-image-bridge-mux",
            )
        val clientProof =
            ImageBridgeHandshakeProtocol.clientProof(
                bridgeToken = "bridge-token",
                clientNonce = "client-nonce",
                serverNonce = "server-nonce",
            )

        assertNotEquals(serverProof, clientProof)
        assertTrue(ImageBridgeHandshakeProtocol.proofMatches(serverProof, serverProof))
        assertFalse(ImageBridgeHandshakeProtocol.proofMatches(clientProof, serverProof))
    }

    @Test
    fun `member snapshot reads legacy payload without mention user id`() {
        val buffer = ByteArrayOutputStream()
        LengthPrefixedFrameCodec.writePayload(
            buffer,
            """
            {
              "status":"ok",
              "memberSnapshot":{
                "roomId":42,
                "scannedAtEpochMs":1234,
                "members":[{"userId":7,"nickname":"alice"}]
              }
            }
            """.trimIndent(),
        )

        val restored = ImageBridgeProtocol.readResponseFrame(ByteArrayInputStream(buffer.toByteArray()))

        assertNull(
            restored.memberSnapshot
                ?.members
                ?.single()
                ?.mentionUserId,
        )
    }

    @Test
    fun `member snapshot preserves mention user id when present`() {
        val response =
            ImageBridgeProtocol.ImageBridgeResponse(
                status = ImageBridgeProtocol.STATUS_OK,
                memberSnapshot =
                    ImageBridgeProtocol.ChatRoomMembersSnapshot(
                        roomId = 42L,
                        scannedAtEpochMs = 1234L,
                        members =
                            listOf(
                                ImageBridgeProtocol.ChatRoomMemberSnapshot(
                                    userId = 7L,
                                    nickname = "alice",
                                    mentionUserId = "mention-alice",
                                ),
                            ),
                    ),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeProtocol.writeFrame(buffer, response)

        val payload = LengthPrefixedFrameCodec.readPayload(ByteArrayInputStream(buffer.toByteArray()))
        assertTrue(payload.contains(""""mentionUserId":"mention-alice""""))

        val restored = ImageBridgeProtocol.readResponseFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(
            "mention-alice",
            restored.memberSnapshot
                ?.members
                ?.single()
                ?.mentionUserId,
        )
    }

    @Test
    fun `member snapshot omits null mention user id`() {
        val response =
            ImageBridgeProtocol.ImageBridgeResponse(
                status = ImageBridgeProtocol.STATUS_OK,
                memberSnapshot =
                    ImageBridgeProtocol.ChatRoomMembersSnapshot(
                        roomId = 42L,
                        scannedAtEpochMs = 1234L,
                        members =
                            listOf(
                                ImageBridgeProtocol.ChatRoomMemberSnapshot(
                                    userId = 7L,
                                    nickname = "alice",
                                    mentionUserId = null,
                                ),
                            ),
                    ),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeProtocol.writeFrame(buffer, response)

        val payload = LengthPrefixedFrameCodec.readPayload(ByteArrayInputStream(buffer.toByteArray()))
        assertFalse(payload.contains("mentionUserId"))
    }

    @Test
    fun `member hint reads legacy payload without mention user id`() {
        val buffer = ByteArrayOutputStream()
        LengthPrefixedFrameCodec.writePayload(
            buffer,
            """
            {
              "action":"snapshot_chatroom_members",
              "roomId":42,
              "memberHints":[{"userId":7,"nickname":"alice"}]
            }
            """.trimIndent(),
        )

        val restored = ImageBridgeProtocol.readRequestFrame(ByteArrayInputStream(buffer.toByteArray()))

        assertNull(restored.memberHints.single().mentionUserId)
    }

    @Test
    fun `member hint preserves mention user id when present`() {
        val request =
            ImageBridgeProtocol.buildSnapshotChatRoomMembersRequest(
                roomId = 42L,
                memberHints =
                    listOf(
                        ImageBridgeProtocol.ChatRoomMemberHint(
                            userId = 7L,
                            nickname = "alice",
                            mentionUserId = "text-ping-7",
                        ),
                    ),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeProtocol.writeFrame(buffer, request)

        val payload = LengthPrefixedFrameCodec.readPayload(ByteArrayInputStream(buffer.toByteArray()))
        assertTrue(payload.contains(""""mentionUserId":"text-ping-7""""))

        val restored = ImageBridgeProtocol.readRequestFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals("text-ping-7", restored.memberHints.single().mentionUserId)
    }

    @Test
    fun `member extraction plan preserves mention user id path`() {
        val request =
            ImageBridgeProtocol.buildSnapshotChatRoomMembersRequest(
                roomId = 42L,
                preferredMemberPlan =
                    ImageBridgeProtocol.ChatRoomMemberExtractionPlan(
                        containerPath = "$.members",
                        sourceClassName = "FakeMember",
                        userIdPath = "id",
                        nicknamePath = "profile.nickname",
                        mentionUserIdPath = "profile.mentionUserId",
                        fingerprint = "$.members|FakeMember|id|profile.nickname|profile.mentionUserId",
                    ),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeProtocol.writeFrame(buffer, request)

        val restored = ImageBridgeProtocol.readRequestFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals("profile.mentionUserId", restored.preferredMemberPlan?.mentionUserIdPath)
    }

    @Test
    fun `fetch member profiles request preserves member ids`() {
        val request =
            ImageBridgeProtocol.buildFetchMemberProfilesRequest(
                roomId = 42L,
                memberIds = listOf(90_001L, 90_002L),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeProtocol.writeFrame(buffer, request)

        val restored = ImageBridgeProtocol.readRequestFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(ImageBridgeProtocol.ACTION_FETCH_MEMBER_PROFILES, restored.action)
        assertEquals(listOf(90_001L, 90_002L), restored.memberIds)
    }
}
