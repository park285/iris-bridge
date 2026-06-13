@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBridgeRequestHandlerChatRoomActionTest {
    @Test
    fun `inspect chatroom action returns introspection payload`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                chatRoomInspector = { roomId -> "{\"chatId\":$roomId}" },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                inspectChatRoomRequest(roomId = 77L),
            )

        assertEquals(ImageBridgeProtocol.STATUS_OK, response.status)
        assertEquals("{\"chatId\":77}", response.inspectionJson)
    }

    @Test
    fun `inspect chatroom action fails when room id is missing`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                chatRoomInspector = { "{}" },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                ImageBridgeProtocol.ImageBridgeRequest(
                    action = ImageBridgeProtocol.ACTION_INSPECT_CHATROOM,
                    protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("roomId missing", response.error)
    }

    @Test
    fun `open chatroom action delegates to opener and returns ok`() {
        var openedRoomId: Long? = null
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                chatRoomOpener = { roomId -> openedRoomId = roomId },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                openChatRoomRequest(roomId = 77L),
            )

        assertEquals(ImageBridgeProtocol.STATUS_OK, response.status)
        assertEquals(77L, openedRoomId)
        assertEquals("open-request", response.requestId)
    }

    @Test
    fun `open chatroom action fails when opener is unavailable`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                openChatRoomRequest(roomId = 77L),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("chatroom opener unavailable", response.error)
    }

    @Test
    fun `open chatroom action fails when room id is missing`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                chatRoomOpener = { error("should not be called") },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                ImageBridgeProtocol.ImageBridgeRequest(
                    action = ImageBridgeProtocol.ACTION_OPEN_CHATROOM,
                    protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                    requestId = "missing-room-open",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("roomId missing", response.error)
    }

    @Test
    fun `snapshot chatroom members action returns payload`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                chatRoomMemberSnapshotProvider = { roomId, expectedMemberHints, preferredPlan ->
                    assertEquals(listOf(7L, 9L), expectedMemberHints.map { it.userId })
                    assertEquals("Alice", expectedMemberHints.first().nickname)
                    assertEquals("profile.nickname", preferredPlan?.nicknamePath)
                    ImageBridgeProtocol.ChatRoomMembersSnapshot(
                        roomId = roomId,
                        sourcePath = "$.members",
                        sourceClassName = "FakeMember",
                        scannedAtEpochMs = 12L,
                        members = listOf(ImageBridgeProtocol.ChatRoomMemberSnapshot(userId = 7L, nickname = "Alice Updated")),
                        selectedPlan = preferredPlan,
                        confidence = ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH,
                        confidenceScore = 500,
                        usedPreferredPlan = true,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                snapshotChatRoomMembersRequest(
                    roomId = 55L,
                    memberIds = listOf(7L, 9L),
                    memberHints =
                        listOf(
                            ImageBridgeProtocol.ChatRoomMemberHint(userId = 7L, nickname = "Alice"),
                            ImageBridgeProtocol.ChatRoomMemberHint(userId = 9L, nickname = "Bob"),
                        ),
                    preferredMemberPlan =
                        ImageBridgeProtocol.ChatRoomMemberExtractionPlan(
                            containerPath = "$.members",
                            sourceClassName = "FakeMember",
                            userIdPath = "id",
                            nicknamePath = "profile.nickname",
                            fingerprint = "$.members|FakeMember|id|profile.nickname",
                        ),
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_OK, response.status)
        assertEquals(55L, response.memberSnapshot?.roomId)
        assertEquals(
            "Alice Updated",
            response.memberSnapshot
                ?.members
                ?.single()
                ?.nickname,
        )
        assertTrue(response.memberSnapshot?.usedPreferredPlan == true)
    }

    @Test
    fun `fetch member profiles action returns upstream payload without snapshot extraction`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                chatRoomMemberSnapshotProvider = { _, _, _ -> error("snapshot should not be called") },
                memberProfileFetcher = { roomId, userIds ->
                    assertEquals(55L, roomId)
                    assertEquals(listOf(90_001L, 90_002L), userIds)
                    mapOf(
                        90_001L to UpstreamMemberProfile(90_001L, "Member Alpha", null),
                        90_002L to UpstreamMemberProfile(90_002L, "Member Beta", "https://example.test/p.png"),
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                fetchMemberProfilesRequest(
                    roomId = 55L,
                    memberIds = listOf(90_001L, 0L, 90_002L, 90_001L),
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_OK, response.status)
        val members = JSONObject(response.payloadJson.orEmpty()).getJSONArray("members")
        assertEquals(90_001L, members.getJSONObject(0).getLong("userId"))
        assertEquals("Member Alpha", members.getJSONObject(0).getString("nickname"))
        assertEquals(90_002L, members.getJSONObject(1).getLong("userId"))
        assertEquals("Member Beta", members.getJSONObject(1).getString("nickname"))
        assertEquals("https://example.test/p.png", members.getJSONObject(1).getString("profileImageUrl"))
    }
}
