@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.server.BridgeHandshakeValidator
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageBridgeRequestHandlerProtocolAuthTest {
    @Test
    fun `request fails when protocol version is missing`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                ImageBridgeProtocol.ImageBridgeRequest(
                    action = "health",
                    protocolVersion = null,
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("unsupported protocol version", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_UNSUPPORTED_PROTOCOL, response.errorCode)
    }

    @Test
    fun `request fails when bridge token does not match`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = BridgeHandshakeValidator(expectedToken = "bridge-token"),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(roomId = 1L, message = "hello", token = "wrong-token"),
            )

        assertEquals("failed", response.status)
        assertEquals("unauthorized bridge token", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_UNAUTHORIZED, response.errorCode)
    }
}
