package party.qwer.iris.imagebridge.runtime.discovery

import party.qwer.iris.imagebridge.runtime.core.BridgeCoreEnvelope
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BridgeCapabilitiesTest {
    @Test
    fun `current capabilities fail closed when native policy is unavailable`() {
        val capabilities =
            currentBridgeCapabilities(
                registryAvailable = true,
                registryError = null,
                specReady = true,
                notificationActionSupported = true,
                textSendCapability = KakaoTextSendCapability(supported = true, ready = true),
                sendTextEnabled = true,
                sendMarkdownEnabled = true,
                nativeCapabilities = { _, _, _, _, _, _, _, _, _ -> null },
            )

        val expectedReason = "bridge core unavailable to evaluate bridge capabilities"
        listOf(
            capabilities.inspectChatRoom,
            capabilities.openChatRoom,
            capabilities.markChatRoomRead,
            capabilities.snapshotChatRoomMembers,
            capabilities.sendText,
            capabilities.sendMarkdown,
        ).forEach { capability ->
            assertFalse(capability.supported)
            assertFalse(capability.ready)
            assertEquals(expectedReason, capability.reason)
        }
    }

    @Test
    fun `current capabilities fail closed when native policy returns malformed capability fields`() {
        val capabilities =
            currentBridgeCapabilities(
                registryAvailable = true,
                registryError = null,
                specReady = true,
                notificationActionSupported = true,
                textSendCapability = KakaoTextSendCapability(supported = true, ready = true),
                nativeCapabilities = { _, _, _, _, _, _, _, _, _ ->
                    BridgeCoreEnvelope.parse(
                        """
                        {
                          "ok": true,
                          "inspectChatRoomSupported": "yes",
                          "inspectChatRoomReady": "yes",
                          "openChatRoomSupported": "yes",
                          "openChatRoomReady": "yes",
                          "markChatRoomReadSupported": "yes",
                          "markChatRoomReadReady": "yes",
                          "snapshotChatRoomMembersSupported": "yes",
                          "snapshotChatRoomMembersReady": "yes",
                          "sendTextSupported": "yes",
                          "sendTextReady": "yes",
                          "sendMarkdownSupported": "yes",
                          "sendMarkdownReady": "yes"
                        }
                        """.trimIndent(),
                    )
                },
            )

        assertFalse(capabilities.sendText.ready)
        assertEquals("bridge core unavailable to evaluate bridge capabilities", capabilities.sendText.reason)
        assertFalse(capabilities.openChatRoom.ready)
        assertEquals("bridge core unavailable to evaluate bridge capabilities", capabilities.openChatRoom.reason)
    }
}
