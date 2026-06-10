@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.server.BridgeImagePathValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeMetrics
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageBridgeRequestHandlerValidationTest {
    @Test
    fun `send text request fails when message is missing`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                ImageBridgeProtocol.ImageBridgeRequest(
                    action = ImageBridgeProtocol.ACTION_SEND_TEXT,
                    protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                    roomId = 123L,
                    requestId = "missing-message-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("message missing", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("missing-message-req", response.requestId)
    }

    @Test
    fun `send text request fails when message is blank`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "   ",
                    requestId = "blank-message-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("message is blank", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("blank-message-req", response.requestId)
    }

    @Test
    fun `send text request fails when message is too long`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "x".repeat(100_001),
                    requestId = "long-message-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("message is too long: 100001", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("long-message-req", response.requestId)
    }

    @Test
    fun `send text request fails when raw attachment is not a JSON object`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "hello",
                    attachmentJson = "[1,2,3]",
                    requestId = "invalid-attachment-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("attachmentJson must be a JSON object", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("invalid-attachment-req", response.requestId)
    }

    @Test
    fun `send text request fails when raw attachment is too long`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "hello",
                    attachmentJson = """{"x":"${"x".repeat(100_000)}"}""",
                    requestId = "long-attachment-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("attachmentJson is too long: 100008", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("long-attachment-req", response.requestId)
    }

    @Test
    fun `send text request fails when text sender is unavailable`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "hello",
                    requestId = "unavailable-text-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("text sender unavailable", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("unavailable-text-req", response.requestId)
    }

    @Test
    fun `send text request fails when capability is not ready`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "hello",
                    requestId = "text-capability-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals("text sender unavailable", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
        assertEquals("text-capability-req", response.requestId)
    }

    @Test
    fun `unknown action returns failed response`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                ImageBridgeProtocol.ImageBridgeRequest(
                    action = "unknown",
                    protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("unknown action: unknown", response.error)
    }

    @Test
    fun `sender failure returns failed response`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { throw IllegalStateException("send failed") },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                metrics = metrics,
                leaseVerifier = testImageLeaseVerifier(),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    imageLeases =
                        listOf(
                            signedTestImageLease(
                                requestId = "image-request",
                                roomId = 1L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("send failed", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_SEND_FAILED, response.errorCode)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.sendFailure)
        assertEquals(0, health.metrics?.missingRequestId)
        assertEquals(ImageBridgeProtocol.ERROR_SEND_FAILED, health.metrics?.lastSendErrorCode)
        file.delete()
    }

    @Test
    fun `path validation failure returns error code and metric`() {
        val allowedDir = Files.createTempDirectory("iris-bridge-allowed").toFile()
        val outsideFile = Files.createTempFile("iris-bridge-outside", ".png").toFile().apply { writeText("x") }
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(allowedDir.absolutePath),
                metrics = metrics,
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(outsideFile.absolutePath),
                    requestId = "req-path",
                ),
            )

        assertEquals("failed", response.status)
        assertEquals(ImageBridgeProtocol.ERROR_PATH_VALIDATION, response.errorCode)
        assertEquals("req-path", response.requestId)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.pathValidationFailure)
        outsideFile.delete()
        allowedDir.deleteRecursively()
    }

    @Test
    fun `static path validation failure returns path validation error code and metric`() {
        val allowedDir = Files.createTempDirectory("iris-bridge-allowed").toFile()
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(allowedDir.absolutePath),
                metrics = metrics,
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(" "),
                    requestId = "req-static-path",
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("blank image path", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_PATH_VALIDATION, response.errorCode)
        assertEquals("req-static-path", response.requestId)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.pathValidationFailure)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `missing image file returns path validation error code and metric`() {
        val allowedDir = Files.createTempDirectory("iris-bridge-allowed").toFile()
        val missingPath = allowedDir.toPath().resolve("missing.png").toString()
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(allowedDir.absolutePath),
                metrics = metrics,
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(missingPath),
                    requestId = "req-missing",
                ),
            )

        assertEquals("failed", response.status)
        assertEquals(ImageBridgeProtocol.ERROR_PATH_VALIDATION, response.errorCode)
        assertEquals("req-missing", response.requestId)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.pathValidationFailure)
        allowedDir.deleteRecursively()
    }
}
