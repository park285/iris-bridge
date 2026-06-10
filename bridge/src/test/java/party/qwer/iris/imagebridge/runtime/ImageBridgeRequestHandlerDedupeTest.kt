@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.server.BridgeImagePathValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeMetrics
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageBridgeRequestHandlerDedupeTest {
    @Test
    fun `side effect request without requestId fails`() {
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyTextHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                metrics = metrics,
                logError = { _, _, _ -> },
            )

        val response = handler.handle(sendTextRequest(roomId = 123L, message = "hello", requestId = null))

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals(ImageBridgeProtocol.ERROR_MISSING_REQUEST_ID, response.errorCode)
        assertEquals("requestId missing", response.error)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.missingRequestId)
    }

    @Test
    fun `health request with requestId is not deduplicated`() {
        var healthCount = 0
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    healthCount += 1
                    readyTextHealthSnapshot().copy(metrics = metrics.snapshot(), restartCount = healthCount)
                },
                handshakeValidator = developmentHandshakeValidator(),
                metrics = metrics,
                logError = { _, _, _ -> },
            )
        val request =
            ImageBridgeProtocol.ImageBridgeRequest(
                action = ImageBridgeProtocol.ACTION_HEALTH,
                protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                requestId = "read-only-health-1",
                token = "bridge-token",
            )

        val first = handler.handle(request)
        val second = handler.handle(request)

        assertEquals(1, first.restartCount)
        assertEquals(2, second.restartCount)
        assertEquals(2, healthCount)
        val health = handler.handle(healthRequest())
        assertEquals(0, health.metrics?.muxRequestDeduplicated)
    }

    @Test
    fun `open chatroom request without requestId fails before opener`() {
        var openCount = 0
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyTextHealthSnapshot().copy(metrics = metrics.snapshot()) },
                chatRoomOpener = { openCount += 1 },
                handshakeValidator = developmentHandshakeValidator(),
                metrics = metrics,
                logError = { _, _, _ -> },
            )

        val response = handler.handle(openChatRoomRequest(roomId = 77L, requestId = null))

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals(ImageBridgeProtocol.ERROR_MISSING_REQUEST_ID, response.errorCode)
        assertEquals(0, openCount)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.missingRequestId)
    }

    @Test
    fun `duplicate send text request returns cached response and invokes sender once`() {
        var sendCount = 0
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { sendCount += 1 },
                healthProvider = { readyTextHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                metrics = metrics,
            )
        val request = sendTextRequest(roomId = 123L, message = "hello", requestId = "dedupe-text-1")

        val first = handler.handle(request)
        val second = handler.handle(request)

        assertEquals(ImageBridgeProtocol.STATUS_SENT, first.status)
        assertEquals(first, second)
        assertEquals(1, sendCount)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.muxRequestDeduplicated)
    }

    @Test
    fun `duplicate send image request returns cached response and invokes sender once`() {
        var sendCount = 0
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { sendCount += 1 },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                metrics = metrics,
                leaseVerifier = testImageLeaseVerifier(),
            )
        val request =
            sendImageRequest(
                roomId = 123L,
                imagePaths = listOf(file.absolutePath),
                requestId = "dedupe-image-1",
                imageLeases =
                    listOf(
                        signedTestImageLease(
                            requestId = "dedupe-image-1",
                            roomId = 123L,
                            canonicalPath = file.canonicalPath,
                        ),
                    ),
            )

        val first = handler.handle(request)
        val second = handler.handle(request)

        assertEquals(ImageBridgeProtocol.STATUS_SENT, first.status)
        assertEquals(first, second)
        assertEquals(1, sendCount)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.muxRequestDeduplicated)
        file.delete()
    }

    @Test
    fun `duplicate open chatroom request returns cached response and invokes opener once`() {
        var openCount = 0
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                chatRoomOpener = { openCount += 1 },
                handshakeValidator = developmentHandshakeValidator(),
                metrics = metrics,
            )
        val request = openChatRoomRequest(roomId = 77L, requestId = "dedupe-open-1")

        val first = handler.handle(request)
        val second = handler.handle(request)

        assertEquals(ImageBridgeProtocol.STATUS_OK, first.status)
        assertEquals(first, second)
        assertEquals("dedupe-open-1", first.requestId)
        assertEquals(1, openCount)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.muxRequestDeduplicated)
    }

    @Test
    fun `duplicate failing send text request returns same cached failure response`() {
        var sendCount = 0
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = {
                    sendCount += 1
                    error("text send failed")
                },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )
        val request = sendTextRequest(roomId = 123L, message = "hello", requestId = "dedupe-text-fail")

        val first = handler.handle(request)
        val second = handler.handle(request)

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, first.status)
        assertEquals(ImageBridgeProtocol.ERROR_SEND_FAILED, first.errorCode)
        assertEquals("dedupe-text-fail", first.requestId)
        assertEquals(first, second)
        assertEquals(1, sendCount)
    }
}
