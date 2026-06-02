@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.TextSendRequest
import party.qwer.iris.imagebridge.runtime.server.BridgeImageLeaseVerifier
import party.qwer.iris.imagebridge.runtime.server.BridgeImagePathValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeMetrics
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageBridgeRequestHandlerSendTest {
    @Test
    fun `send image request with valid lease delegates to runtime and returns sent response`() {
        var captured: ImageSendRequest? = null
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { request -> captured = request },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                metrics = metrics,
                leaseVerifier = BridgeImageLeaseVerifier(expectedToken = "bridge-token", acceptLegacyRawPath = false),
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 123L,
                    imagePaths = listOf(file.absolutePath),
                    threadId = 55L,
                    threadScope = 3,
                    requestId = "req-1",
                    imageLeases =
                        listOf(
                            signedImageLease(
                                secret = "bridge-token",
                                requestId = "req-1",
                                roomId = 123L,
                                canonicalPath = file.canonicalPath,
                            ),
                        ),
                ),
            )

        assertEquals(123L, captured?.roomId)
        assertEquals(listOf(file.canonicalPath), captured?.imagePaths?.map { it.canonicalPath })
        assertEquals(55L, captured?.threadId)
        assertEquals(3, captured?.threadScope)
        assertEquals("req-1", captured?.requestId)
        assertEquals("sent", response.status)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.sendSuccess)
        assertEquals("req-1", health.metrics?.lastSendRequestId)
        assertNotNull(health.metrics?.lastSendDurationMs)
        file.delete()
    }

    @Test
    fun `send image request without lease is rejected when legacy raw path is disabled`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called without a valid lease") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = BridgeImageLeaseVerifier(expectedToken = "bridge-token", acceptLegacyRawPath = false),
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 123L,
                    imagePaths = listOf(file.absolutePath),
                    requestId = "req-no-lease",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        file.delete()
    }

    @Test
    fun `send image request without lease is accepted when legacy raw path is enabled`() {
        var captured: ImageSendRequest? = null
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { request -> captured = request },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = BridgeImageLeaseVerifier(expectedToken = "bridge-token", acceptLegacyRawPath = true),
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 123L,
                    imagePaths = listOf(file.absolutePath),
                    requestId = "req-legacy",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_SENT, response.status)
        assertEquals(listOf(file.canonicalPath), captured?.imagePaths?.map { it.canonicalPath })
        file.delete()
    }

    @Test
    fun `send image request with mismatched lease path is rejected`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called for an unauthorized path") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                leaseVerifier = BridgeImageLeaseVerifier(expectedToken = "bridge-token", acceptLegacyRawPath = false),
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 123L,
                    imagePaths = listOf(file.absolutePath),
                    requestId = "req-mismatch",
                    imageLeases =
                        listOf(
                            signedImageLease(
                                secret = "bridge-token",
                                requestId = "req-mismatch",
                                roomId = 123L,
                                canonicalPath = "${file.canonicalPath}.other",
                            ),
                        ),
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        file.delete()
    }

    @Test
    fun `send text request delegates to text sender and returns sent response`() {
        var captured: TextSendRequest? = null
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { request -> captured = request },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "hello",
                    threadId = 55L,
                    threadScope = 3,
                    mentionsJson = """{"mentions":[7]}""",
                    requestId = "text-req-1",
                ),
            )

        val textRequest = assertNotNull(captured)
        assertEquals(123L, textRequest.roomId)
        assertEquals("hello", textRequest.message)
        assertFalse(textRequest.markdown)
        assertEquals(55L, textRequest.threadId)
        assertEquals(3, textRequest.threadScope)
        assertEquals("""{"mentions":[7]}""", textRequest.mentionsJson)
        assertEquals("text-req-1", textRequest.requestId)
        assertEquals(ImageBridgeProtocol.STATUS_SENT, response.status)
        assertEquals("text-req-1", response.requestId)
    }

    @Test
    fun `send text request delegates raw attachment to text sender`() {
        var captured: TextSendRequest? = null
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { request -> captured = request },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "card title",
                    attachmentJson = """{"P":{"TP":"List"},"K":{"ti":"121065"}}""",
                    requestId = "card-req-1",
                ),
            )

        val textRequest = assertNotNull(captured)
        assertEquals("""{"P":{"TP":"List"},"K":{"ti":"121065"}}""", textRequest.attachmentJson)
        assertEquals(ImageBridgeProtocol.STATUS_SENT, response.status)
    }

    @Test
    fun `send markdown request rejects raw attachment`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                sendMarkdownRequest(
                    roomId = 123L,
                    message = "**card**",
                    attachmentJson = """{"P":{"TP":"List"}}""",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
    }

    @Test
    fun `send text request rejects raw attachment with mentions`() {
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { error("should not be called") },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "@alice card",
                    mentionsJson = """{"mentions":[{"user_id":1}]}""",
                    attachmentJson = """{"P":{"TP":"List"}}""",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
        assertEquals(ImageBridgeProtocol.ERROR_BAD_REQUEST, response.errorCode)
    }

    @Test
    fun `send markdown request delegates markdown flag to text sender`() {
        var captured: TextSendRequest? = null
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { request -> captured = request },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                sendMarkdownRequest(
                    roomId = 123L,
                    message = "**hello**",
                    threadId = 55L,
                    threadScope = 3,
                    mentionsJson = """{"mentions":[9]}""",
                    requestId = "markdown-req-1",
                ),
            )

        val markdownRequest = assertNotNull(captured)
        assertEquals(123L, markdownRequest.roomId)
        assertEquals("**hello**", markdownRequest.message)
        assertTrue(markdownRequest.markdown)
        assertEquals(55L, markdownRequest.threadId)
        assertEquals(3, markdownRequest.threadScope)
        assertEquals("""{"mentions":[9]}""", markdownRequest.mentionsJson)
        assertEquals("markdown-req-1", markdownRequest.requestId)
        assertEquals(ImageBridgeProtocol.STATUS_SENT, response.status)
        assertEquals("markdown-req-1", response.requestId)
    }

    @Test
    fun `send text requests serialize by room and thread`() {
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstCompleted = AtomicBoolean(false)
        val secondObservedFirstComplete = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(2)
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { request ->
                    if (request.message == "first") {
                        firstEntered.countDown()
                        assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                        firstCompleted.set(true)
                    } else {
                        secondObservedFirstComplete.set(firstCompleted.get())
                    }
                },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        try {
            val first =
                executor.submit<ImageBridgeProtocol.ImageBridgeResponse> {
                    handler.handle(
                        sendTextRequest(
                            roomId = 123L,
                            message = "first",
                            threadId = 55L,
                            requestId = "serialize-first",
                        ),
                    )
                }
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            val second =
                executor.submit<ImageBridgeProtocol.ImageBridgeResponse> {
                    handler.handle(
                        sendTextRequest(
                            roomId = 123L,
                            message = "second",
                            threadId = 55L,
                            requestId = "serialize-second",
                        ),
                    )
                }
            releaseFirst.countDown()

            assertEquals(ImageBridgeProtocol.STATUS_SENT, first.get(2, TimeUnit.SECONDS).status)
            assertEquals(ImageBridgeProtocol.STATUS_SENT, second.get(2, TimeUnit.SECONDS).status)
            assertTrue(secondObservedFirstComplete.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `send text request records send metrics`() {
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = {},
                healthProvider = { readyTextHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
                metrics = metrics,
            )

        val response =
            handler.handle(
                sendTextRequest(
                    roomId = 123L,
                    message = "hello",
                    requestId = "text-metrics-req",
                ),
            )

        assertEquals(ImageBridgeProtocol.STATUS_SENT, response.status)
        val health = handler.handle(healthRequest())
        assertEquals(1, health.metrics?.sendSuccess)
        assertEquals("text-metrics-req", health.metrics?.lastSendRequestId)
        assertNotNull(health.metrics?.lastSendDurationMs)
    }
}
