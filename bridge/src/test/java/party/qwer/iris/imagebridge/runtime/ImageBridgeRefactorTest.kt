@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.app.Application
import com.kakao.talk.manager.ShareManager
import org.json.JSONObject
import party.qwer.iris.ImageBridgeHandshakeFrame
import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot
import party.qwer.iris.imagebridge.runtime.discovery.DiscoveryHookStatus
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_SINGLE
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_ENTRY
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_INJECT
import party.qwer.iris.imagebridge.runtime.discovery.currentBridgeCapabilities
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.KakaoImageSender
import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvocationFactory
import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvoker
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendInvocationFactory
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor
import party.qwer.iris.imagebridge.runtime.send.TextSendRequest
import party.qwer.iris.imagebridge.runtime.send.ThreadedChatMediaEntryInvoker
import party.qwer.iris.imagebridge.runtime.send.selectThreadedImageInjectBindingsForTest
import party.qwer.iris.imagebridge.runtime.send.selectThreadedImageInjectMethodForTest
import party.qwer.iris.imagebridge.runtime.server.BridgeHandshakeValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeImagePathValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeMetrics
import party.qwer.iris.imagebridge.runtime.server.BridgeMuxSession
import party.qwer.iris.imagebridge.runtime.server.BridgeMuxSocket
import party.qwer.iris.imagebridge.runtime.server.BridgePeerIdentityValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeSecurityMode
import party.qwer.iris.imagebridge.runtime.server.BridgeSocketHandshakeAuthenticator
import party.qwer.iris.imagebridge.runtime.server.BridgeSpecCheck
import party.qwer.iris.imagebridge.runtime.server.BridgeSpecStatus
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitiesSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitySnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeHealthSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import party.qwer.iris.imagebridge.runtime.server.toJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageBridgeRequestHandlerTest {
    @Test
    fun `send image request delegates to runtime and returns sent response`() {
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
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 123L,
                    imagePaths = listOf(file.absolutePath),
                    threadId = 55L,
                    threadScope = 3,
                    requestId = "req-1",
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
            )
        val request = sendImageRequest(roomId = 123L, imagePaths = listOf(file.absolutePath), requestId = "dedupe-image-1")

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
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(roomId = 1L, imagePaths = listOf(file.absolutePath)),
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

    @Test
    fun `health action returns spec snapshot`() {
        val healthSnapshot =
            ImageBridgeHealthSnapshot(
                running = true,
                specStatus =
                    BridgeSpecStatus(
                        ready = false,
                        checkedAtEpochMs = 1234L,
                        checks = listOf(BridgeSpecCheck(name = "class bh.c", ok = false, detail = "missing")),
                    ),
                discoverySnapshot =
                    BridgeDiscoverySnapshot(
                        installAttempted = true,
                        hooks =
                            listOf(
                                DiscoveryHookStatus(
                                    name = HOOK_SEND_MULTIPLE,
                                    installed = true,
                                    invocationCount = 4,
                                    lastSeenEpochMs = 99L,
                                    lastSummary = "uris=2",
                                ),
                            ),
                    ),
                capabilities =
                    ImageBridgeCapabilitiesSnapshot(
                        inspectChatRoom = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                        openChatRoom = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                        snapshotChatRoomMembers = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                        sendText =
                            ImageBridgeCapabilitySnapshot(
                                supported = false,
                                ready = false,
                                reason = "text sender unavailable",
                            ),
                        sendMarkdown =
                            ImageBridgeCapabilitySnapshot(
                                supported = false,
                                ready = false,
                                reason = "text sender unavailable",
                            ),
                    ),
                restartCount = 3,
                lastCrashMessage = "bind failed",
            )
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { healthSnapshot },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                healthRequest(),
            )

        assertEquals(party.qwer.iris.ImageBridgeProtocol.STATUS_OK, response.status)
        assertFalse(response.specReady == true)
        assertEquals(3, response.restartCount)
        assertEquals("bind failed", response.lastCrashMessage)
        assertEquals(1, response.checks.size)
        assertEquals(1, response.discovery?.hooks?.size)
        assertTrue(response.capabilities?.openChatRoom?.ready == true)
        assertTrue(response.capabilities?.snapshotChatRoomMembers?.ready == true)
        assertFalse(response.capabilities?.sendText?.supported == true)
        assertEquals("text sender unavailable", response.capabilities?.sendText?.reason)
        val jsonCapabilities = healthSnapshot.toJson().getJSONObject("capabilities")
        assertFalse(jsonCapabilities.getJSONObject("sendText").getBoolean("supported"))
        assertFalse(jsonCapabilities.getJSONObject("sendMarkdown").getBoolean("ready"))
    }

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
    fun `send image request fails closed when required discovery hook is not installed`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot =
                            BridgeDiscoverySnapshot(
                                installAttempted = true,
                                hooks = listOf(DiscoveryHookStatus(name = HOOK_SEND_SINGLE, installed = false, invocationCount = 0)),
                            ),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(roomId = 1L, imagePaths = listOf(file.absolutePath)),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hook not ready: ChatMediaSender#sendSingle", response.error)
        file.delete()
    }

    @Test
    fun `send image request fails when discovery installation never ran`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot = BridgeDiscoverySnapshot(installAttempted = false, hooks = emptyList()),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(roomId = 1L, imagePaths = listOf(file.absolutePath)),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hooks not installed", response.error)
        file.delete()
    }

    @Test
    fun `threaded send image request fails closed when threaded discovery hook is not installed`() {
        val file = Files.createTempFile("iris-bridge", ".png").toFile().apply { writeText("x") }
        val rootDir = file.parentFile ?: error("temp file parent missing")
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = {
                    ImageBridgeHealthSnapshot(
                        running = true,
                        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
                        discoverySnapshot =
                            BridgeDiscoverySnapshot(
                                installAttempted = true,
                                hooks =
                                    listOf(
                                        DiscoveryHookStatus(name = HOOK_SEND_SINGLE, installed = true, invocationCount = 0),
                                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_ENTRY, installed = false, invocationCount = 0),
                                    ),
                            ),
                        restartCount = 0,
                        lastCrashMessage = null,
                    )
                },
                handshakeValidator = developmentHandshakeValidator(),
                pathValidator = BridgeImagePathValidator(rootDir.absolutePath),
                logError = { _, _, _ -> },
            )

        val response =
            handler.handle(
                sendImageRequest(
                    roomId = 1L,
                    imagePaths = listOf(file.absolutePath),
                    threadId = 55L,
                    threadScope = 2,
                ),
            )

        assertEquals("failed", response.status)
        assertEquals("bridge discovery hook not ready: ChatMediaSender#threadedEntry", response.error)
        file.delete()
    }

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
                healthRequest(token = "wrong-token"),
            )

        assertEquals("failed", response.status)
        assertEquals("unauthorized bridge token", response.error)
        assertEquals(ImageBridgeProtocol.ERROR_UNAUTHORIZED, response.errorCode)
    }
}

class KakaoTextSendInvocationFactoryTest {
    @Test
    fun `factory records mention context and resolves ShareManager text path by signature`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val mentionContexts = ReplyMentionPendingContextStore()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                mentionPendingContexts = mentionContexts,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "@alice hello",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = """{"mentions":[{"user_id":"text-alice","at":[1],"len":5}]}""",
            requestId = "req-mention",
        )

        assertEquals(chatRoom, ShareManager.chatRoom)
        assertEquals("@alice hello", ShareManager.message)
        assertEquals(false, ShareManager.flag)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
        val pending = assertNotNull(mentionContexts.match(123L, "@alice hello", "req-mention"))
        val mention = JSONObject(pending.attachmentText).getJSONArray("mentions").getJSONObject(0)
        assertEquals("text-alice", mention.getString("user_id"))
        assertEquals("req-mention", pending.sessionId)
    }

    @Test
    fun `factory builds text sending log and invokes request method`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))

        val capability = factory.capability()
        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "@alice hello",
            markdown = true,
            threadId = 55L,
            threadScope = 3,
            mentionsJson = """{"mentions":[{"user_id":7}]}""",
            requestId = "req-text",
        )

        assertTrue(capability.ready)
        assertEquals(chatRoom, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertFalse(FakeTextRequestRecorder.shouldRetry)
        assertEquals(null, FakeTextRequestRecorder.listener)
        val sendingLog = FakeTextRequestRecorder.sendingLog as FakeTextSendingLog
        assertEquals(123L, sendingLog.getChatRoomId())
        assertEquals("@alice hello", sendingLog.f0())
        assertEquals("com.kakao.talk.manager.ShareManager", sendingLog.originClass?.name)
        assertEquals("FM", sendingLog.originTag)
        assertEquals(55L, sendingLog.V0)
        assertEquals(3, sendingLog.Z)
        val attachment = JSONObject(requireNotNull(sendingLog.G))
        assertTrue(attachment.getBoolean("markdown"))
        assertFalse(attachment.has("irisSessionId"))
        assertEquals(1, attachment.getJSONArray("mentions").length())
    }

    @Test
    fun `factory resolves request singleton from enclosing request class`() {
        FakeTextRequestRecorder.reset()
        val registry = buildFakeRegistry()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeOuterTextRequest.CompanionApi::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(124L))

        val capability = factory.capability()
        factory.send(
            roomId = 124L,
            chatRoom = chatRoom,
            message = "outer singleton",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            requestId = "req-outer-text",
        )

        assertTrue(capability.ready)
        assertEquals(chatRoom, FakeTextRequestRecorder.chatRoom)
        val sendingLog = FakeTextRequestRecorder.sendingLog as FakeTextSendingLog
        assertEquals("outer singleton", sendingLog.f0())
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.listener)
        assertEquals(null, sendingLog.G)
    }

    @Test
    fun `factory capability fails closed when request method is unavailable`() {
        val factory =
            KakaoTextSendInvocationFactory(
                registry = buildFakeRegistry(),
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { MissingTextRequestCompanion::class.java },
            )

        val capability = factory.capability()

        assertFalse(capability.ready)
        assertTrue(capability.reason?.contains("ChatSendingLogRequest direct text dispatch") == true)
    }

    @Test
    fun `current capabilities exposes ready direct text sender`() {
        val capabilities =
            currentBridgeCapabilities(
                registryAvailable = true,
                registryError = null,
                specReady = true,
                textSendCapability = KakaoTextSendCapability(supported = true, ready = true),
                sendTextEnabled = true,
                sendMarkdownEnabled = true,
            )

        assertTrue(capabilities.sendText.ready)
        assertTrue(capabilities.sendMarkdown.ready)
    }

    @Test
    fun `current capabilities exposes direct text ready by default`() {
        val capabilities =
            currentBridgeCapabilities(
                registryAvailable = true,
                registryError = null,
                specReady = true,
                textSendCapability = KakaoTextSendCapability(supported = true, ready = true),
            )

        assertTrue(capabilities.sendText.ready)
        assertEquals(null, capabilities.sendText.reason)
        assertTrue(capabilities.sendMarkdown.ready)
        assertEquals(null, capabilities.sendMarkdown.reason)
    }

    @Test
    fun `text bridge rollout flags parse truthy values`() {
        assertTrue(ImageBridgeServer.isTextBridgeSendTextEnabled("yes"))
        assertTrue(ImageBridgeServer.isTextBridgeSendMarkdownEnabled("1"))
        assertFalse(ImageBridgeServer.isTextBridgeSendTextEnabled("false"))
        assertTrue(ImageBridgeServer.isTextBridgeSendMarkdownEnabled(null))
    }
}

class ChatRoomIntentMetadataResolverTest {
    @Test
    fun `resolves Kakao chatroom type value from room`() {
        val resolver = ChatRoomIntentMetadataResolver { FakeRoom(FakeChatRoomType.OpenMulti) }

        assertEquals("OM", resolver.resolveChatRoomType(123L))
    }

    @Test
    fun `falls back to enum name when value accessor is unavailable`() {
        val resolver = ChatRoomIntentMetadataResolver { FakeRoomWithoutValue(FallbackType.NormalMulti) }

        assertEquals("NormalMulti", resolver.resolveChatRoomType(123L))
    }

    @Test
    fun `returns null when room cannot be resolved`() {
        val resolver = ChatRoomIntentMetadataResolver { null }

        assertEquals(null, resolver.resolveChatRoomType(123L))
    }

    private class FakeRoom(
        private val type: FakeChatRoomType,
    ) {
        @Suppress("unused")
        fun y1(): FakeChatRoomType = type
    }

    private enum class FakeChatRoomType(
        private val value: String,
    ) {
        OpenMulti("OM"),
        ;

        @Suppress("unused")
        fun getValue(): String = value
    }

    private class FakeRoomWithoutValue(
        private val type: FallbackType,
    ) {
        @Suppress("unused")
        fun y1(): FallbackType = type
    }

    private enum class FallbackType {
        NormalMulti,
    }
}

private fun sendImageRequest(
    roomId: Long,
    imagePaths: List<String>,
    threadId: Long? = null,
    threadScope: Int? = null,
    requestId: String? = "image-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSendImageRequest(
        roomId = roomId,
        imagePaths = imagePaths,
        threadId = threadId,
        threadScope = threadScope,
        requestId = requestId,
        token = token,
    )

private fun sendTextRequest(
    roomId: Long,
    message: String,
    threadId: Long? = null,
    threadScope: Int? = null,
    mentionsJson: String? = null,
    requestId: String? = "text-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSendTextRequest(
        roomId = roomId,
        message = message,
        threadId = threadId,
        threadScope = threadScope,
        mentionsJson = mentionsJson,
        requestId = requestId,
        token = token,
    )

private fun sendMarkdownRequest(
    roomId: Long,
    message: String,
    threadId: Long? = null,
    threadScope: Int? = null,
    mentionsJson: String? = null,
    requestId: String? = "markdown-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSendMarkdownRequest(
        roomId = roomId,
        message = message,
        threadId = threadId,
        threadScope = threadScope,
        mentionsJson = mentionsJson,
        requestId = requestId,
        token = token,
    )

private fun healthRequest(token: String? = null): ImageBridgeProtocol.ImageBridgeRequest = ImageBridgeProtocol.buildHealthRequest(token = token)

private fun inspectChatRoomRequest(
    roomId: Long,
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildInspectChatRoomRequest(
        roomId = roomId,
        token = token,
    )

private fun openChatRoomRequest(
    roomId: Long,
    requestId: String? = "open-request",
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildOpenChatRoomRequest(
        roomId = roomId,
        requestId = requestId,
        token = token,
    )

private fun snapshotChatRoomMembersRequest(
    roomId: Long,
    memberIds: List<Long> = emptyList(),
    memberHints: List<ImageBridgeProtocol.ChatRoomMemberHint> = emptyList(),
    preferredMemberPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan? = null,
    token: String? = null,
): ImageBridgeProtocol.ImageBridgeRequest =
    ImageBridgeProtocol.buildSnapshotChatRoomMembersRequest(
        roomId = roomId,
        memberIds = memberIds,
        memberHints = memberHints,
        preferredMemberPlan = preferredMemberPlan,
        token = token,
    )

private fun developmentHandshakeValidator(): BridgeHandshakeValidator =
    BridgeHandshakeValidator(
        expectedToken = "",
        securityMode = BridgeSecurityMode.DEVELOPMENT,
    )

class KakaoSendInvocationFactoryTest {
    @Test
    fun `sendSingle caches reflection classes across invocations`() {
        FakeMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
            )
        val chatRoom = FakeChatRoom()

        factory.sendSingle(
            chatRoom = chatRoom,
            imagePath = "/tmp/first.png",
            threadId = 7L,
            threadScope = 3,
        )
        factory.sendSingle(
            chatRoom = chatRoom,
            imagePath = "/tmp/second.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/first.png", "/tmp/second.png"), FakeMediaSender.sentPaths)
        assertEquals(listOf(true, false), FakeMediaSender.threadFlags)
    }

    @Test
    fun `sendSingle rejects missing image path`() {
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
            )

        assertFailsWith<IllegalArgumentException> {
            factory.sendSingle(
                chatRoom = FakeChatRoom(),
                imagePath = "",
                threadId = null,
                threadScope = null,
            )
        }
    }

    @Test
    fun `sendMultiple uses uri list and multi photo enum`() {
        FakeMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        factory.sendMultiple(
            chatRoom = FakeChatRoom(),
            imagePaths = listOf("/tmp/a.png", "/tmp/b.png"),
            threadId = 1L,
            threadScope = 3,
        )

        assertEquals(listOf("/tmp/a.png", "/tmp/b.png"), FakeMediaSender.multiSentUris)
        assertEquals(FakeMessageType.MultiPhoto, FakeMediaSender.multiType)
        assertEquals(FakeWriteType.None, FakeMediaSender.multiWriteType)
    }

    @Test
    fun `sendSingle resolves sender constructor from assignable chatRoom parameter`() {
        FakePolymorphicMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildPolymorphicRegistry(),
            )

        factory.sendSingle(
            chatRoom = FakeDerivedChatRoom(),
            imagePath = "/tmp/polymorphic.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/polymorphic.png"), FakePolymorphicMediaSender.sentPaths)
    }

    @Test
    fun `sendSingle prefers exact sender constructor over assignable one`() {
        ExactPreferredMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildExactPreferredRegistry(),
            )

        factory.sendSingle(
            chatRoom = FakeDerivedChatRoom(),
            imagePath = "/tmp/exact.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(1, ExactPreferredMediaSender.exactCalls)
        assertEquals(0, ExactPreferredMediaSender.baseCalls)
    }

    @Test
    fun `sendSingle accepts sender constructor with primitive long thread parameter`() {
        PrimitiveThreadParamMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildPrimitiveThreadParamRegistry(),
            )

        factory.sendSingle(
            chatRoom = FakeChatRoom(),
            imagePath = "/tmp/primitive-thread.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/primitive-thread.png"), PrimitiveThreadParamMediaSender.sentPaths)
    }
}

class ThreadedChatMediaEntryInvokerTest {
    @Test
    fun `threaded entry invoker resolves method by signature when obfuscated name changes`() {
        RenamedThreadedEntryMediaSender.reset()
        val invoker =
            ThreadedChatMediaEntryInvoker(
                registry = buildRenamedThreadedRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        invoker.invoke(
            sender =
                RenamedThreadedEntryMediaSender(
                    chatRoom = FakeChatRoom(),
                    threadId = 3805486995143352321L,
                    sendWithThread = { false },
                    attachmentDecorator = { payload -> payload },
                ),
            imagePaths = listOf("/tmp/thread-a.png", "/tmp/thread-b.png"),
        )

        assertEquals(listOf("/tmp/thread-a.png", "/tmp/thread-b.png"), RenamedThreadedEntryMediaSender.sentUris)
        assertEquals(FakeMessageType.MultiPhoto, RenamedThreadedEntryMediaSender.lastType)
        assertEquals(FakeWriteType.Connect, RenamedThreadedEntryMediaSender.lastWriteType)
    }
}

class ThreadedImageXposedInjectorSelectorTest {
    @Test
    fun `threaded image injector selector resolves method by signature when obfuscated name changes`() {
        val method =
            selectThreadedImageInjectMethodForTest(
                chatMediaSenderClass = RenamedThreadedInjectMediaSender::class.java,
                writeTypeClass = FakeWriteType::class.java,
                listenerClass = FakeListener::class.java,
            )

        assertEquals("z", method.name)
    }

    @Test
    fun `threaded image injector prefers request dispatch hook when available`() {
        val bindings =
            selectThreadedImageInjectBindingsForTest(
                requestCompanionClass = FakeThreadedRequestCompanion::class.java,
                chatMediaSenderClass = RenamedThreadedInjectMediaSender::class.java,
                chatRoomClass = FakeChatRoomModel::class.java,
                writeTypeClass = FakeWriteType::class.java,
                listenerClass = FakeListener::class.java,
            )

        assertEquals(listOf("request", "legacy"), bindings.map { it.source })
        assertEquals(listOf("u", "z"), bindings.map { it.method.name })
        assertEquals(listOf(1, 0), bindings.map { it.sendingLogArgIndex })
    }
}

class ChatRoomResolverTest {
    @Test
    fun `resolve uses database path with registry`() {
        FakeChatRuntime.reset()
        val registry = buildFakeRegistry()
        val resolver = ChatRoomResolver(registry = registry)

        val first = resolver.resolve(101L)
        val second = resolver.resolve(102L)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(listOf(101L, 102L), FakeChatRuntime.resolvedRoomIds)
    }

    @Test
    fun `resolve prefers exact legacy companion resolver name`() {
        FakeChatRuntime.reset()
        LegacyNameSensitiveRecorder.calls.clear()
        val resolver = ChatRoomResolver(registry = buildLegacyNameSensitiveRegistry())

        resolver.resolve(777L)

        assertEquals(listOf("c"), LegacyNameSensitiveRecorder.calls)
    }
}

class KakaoImageSenderTest {
    @Test
    fun `threaded image send routes through threaded invoker`() {
        val invoker = RecordingKakaoSendInvoker()
        val sender =
            KakaoImageSender(
                chatRoomResolver = { FakeChatRoom() },
                sendInvocationFactory = invoker,
                logInfo = { _, _ -> },
            )

        sender.send(
            roomId = 18478615493603057L,
            imagePaths = listOf("/tmp/thread.png"),
            threadId = 3805486995143352321L,
            threadScope = 3,
            requestId = "req-thread",
        )

        assertEquals(0, invoker.singleCalls)
        assertEquals(0, invoker.multiCalls)
        assertEquals(1, invoker.threadedCalls)
        assertEquals(18478615493603057L, invoker.lastRoomId)
        assertEquals(listOf("/tmp/thread.png"), invoker.lastImagePaths)
        assertEquals(3805486995143352321L, invoker.lastThreadId)
        assertEquals(3, invoker.lastThreadScope)
    }

    @Test
    fun `room image send still routes through single invoker`() {
        val invoker = RecordingKakaoSendInvoker()
        val sender =
            KakaoImageSender(
                chatRoomResolver = { FakeChatRoom() },
                sendInvocationFactory = invoker,
                logInfo = { _, _ -> },
            )

        sender.send(
            roomId = 18478615493603057L,
            imagePaths = listOf("/tmp/room.png"),
            threadId = null,
            threadScope = null,
            requestId = "req-room",
        )

        assertEquals(1, invoker.singleCalls)
        assertEquals(0, invoker.multiCalls)
        assertEquals(0, invoker.threadedCalls)
    }
}

class ImageBridgeServerRestartPolicyTest {
    @Test
    fun `restart delay grows exponentially and caps`() {
        assertEquals(1_000L, ImageBridgeServer.nextBridgeRestartDelayMs(1))
        assertEquals(2_000L, ImageBridgeServer.nextBridgeRestartDelayMs(2))
        assertEquals(4_000L, ImageBridgeServer.nextBridgeRestartDelayMs(3))
        assertEquals(30_000L, ImageBridgeServer.nextBridgeRestartDelayMs(99))
    }

    @Test
    fun `client executor uses bounded pool and queue`() {
        val executor = ImageBridgeServer.newClientExecutorForTest()

        try {
            assertEquals(2, executor.corePoolSize)
            assertEquals(8, executor.maximumPoolSize)
            assertEquals(64, executor.queue.remainingCapacity())
        } finally {
            executor.shutdownNow()
        }
    }
}

class BridgeMuxSessionTest {
    @Test
    fun `mux request response preserves correlation id`() {
        val socket =
            FakeBridgeMuxSocket(
                muxFrames(
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                        correlationId = "corr-1",
                        request = healthRequest(),
                    ),
                ),
            )
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = BridgeMetrics(),
            logError = { _, _, _ -> },
        ).run()

        val response = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(socket.outputStream.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_RESPONSE, response.type)
        assertEquals("corr-1", response.correlationId)
        assertEquals(ImageBridgeProtocol.STATUS_OK, response.response?.status)
    }

    @Test
    fun `mux ping returns pong`() {
        val socket =
            FakeBridgeMuxSocket(
                muxFrames(
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_PING,
                        correlationId = "ping-1",
                    ),
                ),
            )
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = BridgeMetrics(),
            logError = { _, _, _ -> },
        ).run()

        val response = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(socket.outputStream.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_PONG, response.type)
        assertEquals("ping-1", response.correlationId)
    }

    @Test
    fun `mux request over in flight limit returns bridge busy`() {
        val socket =
            FakeBridgeMuxSocket(
                muxFrames(
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                        correlationId = "busy-1",
                        request = healthRequest(),
                    ),
                ),
            )
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot().copy(metrics = metrics.snapshot()) },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = metrics,
            maxInFlight = 0,
            logError = { _, _, _ -> },
        ).run()

        val response = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(socket.outputStream.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_RESPONSE, response.type)
        assertEquals("busy-1", response.correlationId)
        assertEquals(ImageBridgeProtocol.ERROR_BRIDGE_BUSY, response.response?.errorCode)
        assertEquals(1, metrics.snapshot().bridgeBusy)
    }

    @Test
    fun `mux cancel before queued request runs suppresses side effect and response`() {
        var sendCount = 0
        val executor = QueuedExecutorService()
        val socket =
            FakeBridgeMuxSocket(
                muxFrames(
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                        correlationId = "cancel-1",
                        request = sendTextRequest(roomId = 123L, message = "hello", requestId = "cancel-req-1"),
                    ),
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_CANCEL,
                        correlationId = "cancel-1",
                    ),
                ),
            )
        val metrics = BridgeMetrics()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                textSender = { sendCount += 1 },
                healthProvider = { readyTextHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = executor,
            handler = handler,
            isRunning = { socket.inputStream.available() > 0 },
            metrics = metrics,
            logError = { _, _, _ -> },
        ).run()
        executor.runNext()

        assertEquals(0, sendCount)
        assertEquals(1, metrics.snapshot().muxRequestCancelled)
        assertEquals(0, socket.outputStream.size())
    }

    @Test
    fun `mux response write failure closes session without escaping executor`() {
        var loggedMessage: String? = null
        val socket =
            FailingOutputBridgeMuxSocket(
                muxFrames(
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                        correlationId = "closed-1",
                        request = healthRequest(),
                    ),
                ),
            )
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = BridgeMetrics(),
            logError = { _, message, _ -> loggedMessage = message },
        ).run()

        assertEquals("bridge mux response write failed", loggedMessage)
        assertTrue(socket.closed)
    }

    @Test
    fun `mux idle timeout closes session without error log`() {
        var logged = false
        val socket = TimeoutBridgeMuxSocket()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = BridgeMetrics(),
            logError = { _, _, _ -> logged = true },
        ).run()

        assertFalse(logged)
        assertTrue(socket.closed)
    }

    @Test
    fun `mux partial frame timeout is logged as protocol failure`() {
        var logged = false
        val socket = PartialPayloadTimeoutBridgeMuxSocket()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = BridgeMetrics(),
            logError = { _, _, _ -> logged = true },
        ).run()

        assertTrue(logged)
        assertTrue(socket.closed)
    }

    @Test
    fun `mux partial length timeout is logged as protocol failure`() {
        var logged = false
        val socket = PartialLengthTimeoutBridgeMuxSocket()
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { readyHealthSnapshot() },
                handshakeValidator = developmentHandshakeValidator(),
            )

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = BridgeMetrics(),
            logError = { _, _, _ -> logged = true },
        ).run()

        assertTrue(logged)
        assertTrue(socket.closed)
    }
}

class BridgeDiscoveryTest {
    @Test
    fun `records discovery hook installation and invocation`() {
        BridgeDiscovery.resetForTest()

        BridgeDiscovery.markInstalledForTest(HOOK_SEND_MULTIPLE)
        BridgeDiscovery.recordForTest(HOOK_SEND_MULTIPLE, "uris=2")

        val snapshot = BridgeDiscovery.snapshot()
        val hook = snapshot.hooks.first { it.name == HOOK_SEND_MULTIPLE }

        assertTrue(snapshot.installAttempted)
        assertTrue(hook.installed)
        assertEquals(1, hook.invocationCount)
        assertEquals("uris=2", hook.lastSummary)
        assertNotNull(hook.lastSeenEpochMs)
    }
}

class RoomThreadSerialExecutorTest {
    @Test
    fun `same room and thread are serialized`() {
        val executor = RoomThreadSerialExecutor()
        val releaseFirst = CountDownLatch(1)
        val firstStarted = CountDownLatch(1)
        val secondRan = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first =
                pool.submit<Unit> {
                    executor.executeSynchronously(roomId = 1L, threadId = 10L) {
                        firstStarted.countDown()
                        releaseFirst.await(3, TimeUnit.SECONDS)
                    }
                }
            val second =
                pool.submit<Unit> {
                    firstStarted.await(3, TimeUnit.SECONDS)
                    executor.executeSynchronously(roomId = 1L, threadId = 10L) {
                        secondRan.set(true)
                    }
                }

            assertFalse(secondRan.get())
            releaseFirst.countDown()
            first.get(3, TimeUnit.SECONDS)
            second.get(3, TimeUnit.SECONDS)
            assertTrue(secondRan.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `different threads in same room can run independently`() {
        val executor = RoomThreadSerialExecutor()
        val releaseFirst = CountDownLatch(1)
        val firstStarted = CountDownLatch(1)
        val secondRan = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first =
                pool.submit<Unit> {
                    executor.executeSynchronously(roomId = 1L, threadId = 10L) {
                        firstStarted.countDown()
                        releaseFirst.await(3, TimeUnit.SECONDS)
                    }
                }
            val second =
                pool.submit<Unit> {
                    firstStarted.await(3, TimeUnit.SECONDS)
                    executor.executeSynchronously(roomId = 1L, threadId = 11L) {
                        secondRan.set(true)
                    }
                }

            second.get(3, TimeUnit.SECONDS)
            assertTrue(secondRan.get())
            releaseFirst.countDown()
            first.get(3, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `lock count stays bounded across many keys`() {
        val executor = RoomThreadSerialExecutor(stripeCount = 8)

        repeat(100) { index ->
            executor.executeSynchronously(roomId = index.toLong(), threadId = index.toLong()) {}
        }

        assertEquals(8, executor.lockCountForTest())
    }
}

class BridgeSecurityTest {
    @Test
    fun `security mode defaults to production unless development is explicitly requested`() {
        assertEquals(BridgeSecurityMode.PRODUCTION, BridgeSecurityMode.fromEnv(null))
        assertEquals(BridgeSecurityMode.PRODUCTION, BridgeSecurityMode.fromEnv("unknown"))
        assertEquals(BridgeSecurityMode.DEVELOPMENT, BridgeSecurityMode.fromEnv("development"))
    }

    @Test
    fun `peer validator rejects unauthorized uid`() {
        val validator = BridgePeerIdentityValidator(setOf(2000))

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(1000)
            }

        assertEquals("unauthorized bridge client uid=1000", error.message)
    }

    @Test
    fun `production mode requires configured token`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "",
                securityMode = BridgeSecurityMode.PRODUCTION,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(healthRequest(token = ""))
            }

        assertEquals("bridge token must be configured in production mode", error.message)
    }

    @Test
    fun `production mode rejects mismatched token`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "secret",
                securityMode = BridgeSecurityMode.PRODUCTION,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(healthRequest(token = "wrong"))
            }

        assertEquals("unauthorized bridge token", error.message)
    }

    @Test
    fun `production mode accepts matching token`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "secret",
                securityMode = BridgeSecurityMode.PRODUCTION,
            )

        validator.validate(healthRequest(token = "secret"))
    }

    @Test
    fun `development mode skips token check when blank`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "",
                securityMode = BridgeSecurityMode.DEVELOPMENT,
            )

        validator.validate(healthRequest(token = ""))
    }

    @Test
    fun `development mode checks token when configured`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "secret",
                securityMode = BridgeSecurityMode.DEVELOPMENT,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(healthRequest(token = "wrong"))
            }

        assertEquals("unauthorized bridge token", error.message)
    }

    @Test
    fun `socket authenticator accepts matching client proof before request handling`() {
        val input = ByteArrayOutputStream()
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeProtocol.buildHello(
                clientNonce = "client-nonce",
                socketName = "iris-image-bridge-mux",
                timestampMs = 1234L,
            ),
        )
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeProtocol.buildClientProof(
                bridgeToken = "bridge-token",
                clientNonce = "client-nonce",
                serverNonce = "server-nonce",
            ),
        )
        val output = ByteArrayOutputStream()
        val authenticator =
            BridgeSocketHandshakeAuthenticator(
                expectedToken = "bridge-token",
                securityMode = BridgeSecurityMode.PRODUCTION,
                nonceFactory = { "server-nonce" },
            )

        authenticator.authenticate(ByteArrayInputStream(input.toByteArray()), output, "iris-image-bridge-mux")

        val serverProof = ImageBridgeHandshakeProtocol.readFrame(ByteArrayInputStream(output.toByteArray()))
        assertEquals(ImageBridgeHandshakeProtocol.TYPE_SERVER_PROOF, serverProof.type)
        assertTrue(
            ImageBridgeHandshakeProtocol.proofMatches(
                serverProof.proof,
                ImageBridgeHandshakeProtocol.serverProof(
                    bridgeToken = "bridge-token",
                    clientNonce = "client-nonce",
                    serverNonce = "server-nonce",
                    socketName = "iris-image-bridge-mux",
                ),
            ),
        )
    }

    @Test
    fun `socket authenticator rejects bad client proof with sanitized error`() {
        val input = ByteArrayOutputStream()
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeProtocol.buildHello(
                clientNonce = "client-nonce",
                socketName = "iris-image-bridge-mux",
                timestampMs = 1234L,
            ),
        )
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeFrame(
                type = ImageBridgeHandshakeProtocol.TYPE_CLIENT_PROOF,
                protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                proof = "bad-proof",
            ),
        )
        val authenticator =
            BridgeSocketHandshakeAuthenticator(
                expectedToken = "bridge-token",
                securityMode = BridgeSecurityMode.PRODUCTION,
                nonceFactory = { "server-nonce" },
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                authenticator.authenticate(ByteArrayInputStream(input.toByteArray()), ByteArrayOutputStream(), "iris-image-bridge-mux")
            }

        assertEquals(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED, error.message)
    }

    @Test
    fun `default allowed uids include configured values in addition to development defaults`() {
        val allowed = BridgePeerIdentityValidator.defaultAllowedUids("2000, 3000")

        assertTrue(allowed.contains(0))
        assertTrue(allowed.contains(2000))
        assertTrue(allowed.contains(3000))
    }

    @Test
    fun `production mode allows root uid by default`() {
        val validator =
            BridgePeerIdentityValidator(
                securityMode = BridgeSecurityMode.PRODUCTION,
                extraUidsRaw = null,
            )

        validator.validate(0)
    }

    @Test
    fun `production mode accepts explicitly allowed uid`() {
        val validator =
            BridgePeerIdentityValidator(
                securityMode = BridgeSecurityMode.PRODUCTION,
                extraUidsRaw = "0,2000",
            )

        validator.validate(0)
    }

    @Test
    fun `development mode allows root and shell by default`() {
        val validator =
            BridgePeerIdentityValidator(
                securityMode = BridgeSecurityMode.DEVELOPMENT,
                extraUidsRaw = null,
            )

        validator.validate(0)
        validator.validate(2000)
    }

    @Test
    fun `path validator rejects files outside allowed root`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val outsideFile = Files.createTempFile("iris-outside", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(listOf(outsideFile.absolutePath))
            }

        assertTrue(error.message?.contains("outside allowed root") == true)
        outsideFile.delete()
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator accepts files inside allowed root`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val insideFile = Files.createTempFile(allowedDir.toPath(), "iris-inside", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val validated = validator.validate(listOf(insideFile.absolutePath))

        assertEquals(listOf(insideFile.canonicalPath), validated.map { it.canonicalPath })
        insideFile.delete()
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator accepts files inside any allowed root`() {
        val legacyDir = Files.createTempDirectory("iris-legacy-allowed").toFile()
        val runtimeDir = Files.createTempDirectory("iris-runtime-allowed").toFile()
        val runtimeFile = Files.createTempFile(runtimeDir.toPath(), "iris-runtime", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(listOf(legacyDir.absolutePath, runtimeDir.absolutePath))

        val validated = validator.validate(listOf(runtimeFile.absolutePath))

        assertEquals(listOf(runtimeFile.canonicalPath), validated.map { it.canonicalPath })
        runtimeFile.delete()
        legacyDir.deleteRecursively()
        runtimeDir.deleteRecursively()
    }

    @Test
    fun `default path validator allows native runtime reply image root`() {
        assertTrue(BridgeImagePathValidator.DEFAULT_ALLOWED_IMAGE_ROOTS.contains("/data/iris/reply-images"))
        assertFalse(BridgeImagePathValidator.DEFAULT_ALLOWED_IMAGE_ROOTS.contains(BridgeImagePathValidator.LEGACY_OUTBOX_IMAGE_ROOT))
    }

    @Test
    fun `default path roots honor runtime data dir policy`() {
        assertEquals(
            listOf("/custom/iris/reply-images"),
            BridgeImagePathValidator.defaultAllowedImageRoots(mapOf("IRIS_DATA_DIR" to "/custom/iris")),
        )
    }

    @Test
    fun `default path roots prefer configured reply image directory`() {
        assertEquals(
            listOf("/config/iris/images"),
            BridgeImagePathValidator.defaultAllowedImageRoots(
                env = mapOf("IRIS_CONFIG_PATH" to "/tmp/config.json"),
                fileReader = { """{"replyImageDir":"/config/iris/images"}""" },
            ),
        )
    }

    @Test
    fun `path validator rejects too many paths`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val files =
            (0..BridgeImagePathValidator.MAX_IMAGE_PATH_COUNT).map { index ->
                Files.createTempFile(allowedDir.toPath(), "iris-$index", ".png").toFile().apply { writeText("x") }
            }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(files.map { it.absolutePath })
            }

        assertTrue(error.message?.contains("too many image paths") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator rejects too long path`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(listOf("/data/iris/reply-images/" + "a".repeat(BridgeImagePathValidator.MAX_IMAGE_PATH_LENGTH)))
            }

        assertTrue(error.message?.contains("too long") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator rejects symlink paths`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val target = Files.createTempFile(allowedDir.toPath(), "iris-target", ".png")
        Files.write(target, byteArrayOf(1))
        val link = allowedDir.toPath().resolve("iris-link.png")
        Files.createSymbolicLink(link, target.fileName)
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(listOf(link.toString()))
            }

        assertTrue(error.message?.contains("symbolic link") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `validated path rejects changed file before send`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val image = Files.createTempFile(allowedDir.toPath(), "iris-image", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)
        val validated = validator.validate(listOf(image.absolutePath)).single()

        Thread.sleep(2)
        image.writeText("changed")

        val error =
            assertFailsWith<IllegalArgumentException> {
                validated.revalidate()
            }

        assertTrue(error.message?.contains("changed before send") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `kakao image sender receives canonical path only`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val image = Files.createTempFile(allowedDir.toPath(), "iris-image", ".png").toFile().apply { writeText("x") }
        val validated = BridgeImagePathValidator(allowedDir.absolutePath).validate(listOf(image.absolutePath))
        val sent = mutableListOf<String>()
        val sender =
            KakaoImageSender(
                chatRoomResolver = { Any() },
                sendInvocationFactory =
                    object : KakaoSendInvoker {
                        override fun sendSingle(
                            chatRoom: Any,
                            imagePath: String,
                            threadId: Long?,
                            threadScope: Int?,
                        ) {
                            sent += imagePath
                        }

                        override fun sendMultiple(
                            chatRoom: Any,
                            imagePaths: List<String>,
                            threadId: Long?,
                            threadScope: Int?,
                        ) {
                            sent += imagePaths
                        }

                        override fun sendThreaded(
                            roomId: Long,
                            chatRoom: Any,
                            imagePaths: List<String>,
                            threadId: Long,
                            threadScope: Int,
                        ) {
                            sent += imagePaths
                        }
                    },
                logInfo = { _, _ -> },
            )

        sender.send(
            ImageSendRequest(
                roomId = 1L,
                imagePaths = validated,
                threadId = null,
                threadScope = null,
                requestId = "req-canonical",
            ),
        )

        assertEquals(listOf(image.canonicalPath), sent)
        allowedDir.deleteRecursively()
    }
}

private class FakeChatRoom

private class RecordingKakaoSendInvoker : KakaoSendInvoker {
    var singleCalls = 0
    var multiCalls = 0
    var threadedCalls = 0
    var lastRoomId: Long? = null
    var lastImagePaths: List<String> = emptyList()
    var lastThreadId: Long? = null
    var lastThreadScope: Int? = null

    override fun sendSingle(
        chatRoom: Any,
        imagePath: String,
        threadId: Long?,
        threadScope: Int?,
    ) {
        singleCalls += 1
    }

    override fun sendMultiple(
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
    ) {
        multiCalls += 1
    }

    override fun sendThreaded(
        roomId: Long,
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long,
        threadScope: Int,
    ) {
        threadedCalls += 1
        lastRoomId = roomId
        lastImagePaths = imagePaths
        lastThreadId = threadId
        lastThreadScope = threadScope
    }
}

private fun muxFrames(vararg frames: ImageBridgeMuxFrame): ByteArray =
    ByteArrayOutputStream()
        .also { output ->
            frames.forEach { frame -> ImageBridgeMuxProtocol.writeFrame(output, frame) }
        }.toByteArray()

private class FakeBridgeMuxSocket(
    input: ByteArray,
    override val peerUid: Int? = 0,
) : BridgeMuxSocket {
    override val inputStream = ByteArrayInputStream(input)
    override val outputStream = ByteArrayOutputStream()

    var readTimeoutMs: Int = 0
        private set
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) {
        readTimeoutMs = timeoutMs
    }

    override fun close() {
        closed = true
    }
}

private class FailingOutputBridgeMuxSocket(
    input: ByteArray,
) : BridgeMuxSocket {
    override val inputStream = ByteArrayInputStream(input)
    override val outputStream =
        object : OutputStream() {
            override fun write(byte: Int) = throw IOException("socket not created")
        }
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

private class TimeoutBridgeMuxSocket : BridgeMuxSocket {
    override val inputStream: InputStream =
        object : InputStream() {
            override fun read(): Int = throw IOException("Try again")
        }
    override val outputStream = ByteArrayOutputStream()
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

private class PartialPayloadTimeoutBridgeMuxSocket : BridgeMuxSocket {
    override val inputStream: InputStream =
        object : InputStream() {
            private val header = byteArrayOf(0, 0, 0, 16)
            private var index = 0

            override fun read(): Int {
                if (index < header.size) {
                    return header[index++].toInt() and 0xff
                }
                throw IOException("Try again")
            }
        }
    override val outputStream = ByteArrayOutputStream()
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

private class PartialLengthTimeoutBridgeMuxSocket : BridgeMuxSocket {
    override val inputStream: InputStream =
        object : InputStream() {
            private var firstRead = true

            override fun read(): Int {
                if (firstRead) {
                    firstRead = false
                    return 0
                }
                throw IOException("Try again")
            }
        }
    override val outputStream = ByteArrayOutputStream()
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

private class DirectExecutorService : AbstractExecutorService() {
    private val shutdown = AtomicBoolean(false)

    override fun shutdown() {
        shutdown.set(true)
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown.set(true)
        return emptyList()
    }

    override fun isShutdown(): Boolean = shutdown.get()

    override fun isTerminated(): Boolean = shutdown.get()

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = true

    override fun execute(command: Runnable) {
        if (shutdown.get()) {
            error("executor shut down")
        }
        command.run()
    }
}

private class QueuedExecutorService : AbstractExecutorService() {
    private val shutdown = AtomicBoolean(false)
    private val tasks = ArrayDeque<Runnable>()

    override fun shutdown() {
        shutdown.set(true)
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown.set(true)
        return tasks.toList().also { tasks.clear() }
    }

    override fun isShutdown(): Boolean = shutdown.get()

    override fun isTerminated(): Boolean = shutdown.get() && tasks.isEmpty()

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = isTerminated

    override fun execute(command: Runnable) {
        if (shutdown.get()) {
            error("executor shut down")
        }
        tasks += command
    }

    fun runNext() {
        tasks.removeFirst().run()
    }
}

private fun readyHealthSnapshot(): ImageBridgeHealthSnapshot =
    ImageBridgeHealthSnapshot(
        running = true,
        specStatus = BridgeSpecStatus(ready = true, checkedAtEpochMs = 1L, checks = emptyList()),
        discoverySnapshot =
            BridgeDiscoverySnapshot(
                installAttempted = true,
                hooks =
                    listOf(
                        DiscoveryHookStatus(name = HOOK_SEND_SINGLE, installed = true, invocationCount = 0),
                        DiscoveryHookStatus(name = HOOK_SEND_MULTIPLE, installed = true, invocationCount = 0),
                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_ENTRY, installed = true, invocationCount = 0),
                        DiscoveryHookStatus(name = HOOK_SEND_THREADED_INJECT, installed = true, invocationCount = 0),
                    ),
            ),
        restartCount = 0,
        lastCrashMessage = null,
    )

private fun readyTextHealthSnapshot(): ImageBridgeHealthSnapshot =
    readyHealthSnapshot().copy(
        capabilities =
            ImageBridgeCapabilitiesSnapshot(
                sendText = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                sendMarkdown = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
            ),
    )

private class FakeMediaItem(
    val path: String,
    val size: Long,
)

private class FakeMediaSender(
    chatRoom: FakeChatRoom,
    private val threadId: Long?,
    private val sendWithThread: () -> Boolean,
    private val attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    private val roomHash = chatRoom.hashCode()

    companion object {
        val sentPaths = mutableListOf<String>()
        val threadFlags = mutableListOf<Boolean>()
        val roomHashes = mutableListOf<Int>()
        val multiSentUris = mutableListOf<String>()
        var multiType: Any? = null
        var multiWriteType: Any? = null

        fun reset() {
            sentPaths.clear()
            threadFlags.clear()
            roomHashes.clear()
            multiSentUris.clear()
            multiType = null
            multiWriteType = null
        }
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        sentPaths += mediaItem.path
        threadFlags += sendWithThread()
        roomHashes += roomHash
        check(!suppressAnimation)
        check(attachmentDecorator(JSONObject()) != null)
        check(threadId == null || threadId >= 0L)
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        multiSentUris += uris.map { uri -> uri.toString().removePrefix("uri:") }
        multiType = type
        multiWriteType = writeType
        check(message == null)
        check(attachment == null)
        check(forwardExtra == null)
        check(!shareOriginal)
        check(!highQuality)
        check(listener == null)
    }
}

private enum class FakeMessageType {
    Text,
    Photo,
    MultiPhoto,
}

private enum class FakeWriteType {
    None,
    Connect,
}

private interface FakeListener

private object FakeTextRequestRecorder {
    var chatRoom: Any? = null
    var sendingLog: Any? = null
    var writeType: FakeWriteType? = null
    var listener: Any? = null
    var shouldRetry: Boolean = true

    fun reset() {
        chatRoom = null
        sendingLog = null
        writeType = null
        listener = null
        shouldRetry = true
    }
}

private class FakeTextRequestCompanion {
    companion object {
        @JvmField
        val f = FakeTextRequestCompanion()
    }

    fun u(
        chatRoom: FakeChatRoomModel,
        sendingLog: FakeTextSendingLog,
        writeType: FakeWriteType?,
        listener: FakeListener?,
        shouldRetry: Boolean,
    ) {
        FakeTextRequestRecorder.chatRoom = chatRoom
        FakeTextRequestRecorder.sendingLog = sendingLog
        FakeTextRequestRecorder.writeType = writeType
        FakeTextRequestRecorder.listener = listener
        FakeTextRequestRecorder.shouldRetry = shouldRetry
    }
}

private class MissingTextRequestCompanion {
    companion object {
        @JvmField
        val f = MissingTextRequestCompanion()
    }
}

private class FakeOuterTextRequest {
    companion object {
        @JvmField
        val f = CompanionApi()
    }

    class CompanionApi {
        fun u(
            chatRoom: FakeChatRoomModel,
            sendingLog: FakeTextSendingLog,
            writeType: FakeWriteType?,
            listener: FakeListener?,
            shouldRetry: Boolean,
        ) {
            FakeTextRequestRecorder.chatRoom = chatRoom
            FakeTextRequestRecorder.sendingLog = sendingLog
            FakeTextRequestRecorder.writeType = writeType
            FakeTextRequestRecorder.listener = listener
            FakeTextRequestRecorder.shouldRetry = shouldRetry
        }
    }
}

private class FakeTextSendingLog private constructor(
    private val roomId: Long,
    private val message: String,
    val originClass: Class<*>?,
    val originTag: String?,
) {
    var G: String? = null
    var Z: Int = 0
    var V0: Long? = null

    fun getChatRoomId(): Long = roomId

    fun f0(): String = message

    fun H1(threadScope: Int) {
        Z = threadScope
    }

    fun J1(threadId: Long?) {
        V0 = threadId
    }

    class b(
        private val roomId: Long,
        private val messageType: FakeMessageType,
        @Suppress("UNUSED_PARAMETER") reserved: Int,
        @Suppress("UNUSED_PARAMETER") messageId: Long?,
        @Suppress("UNUSED_PARAMETER") needsUpload: Boolean,
    ) {
        constructor(
            chatRoom: FakeChatRoomModel,
            messageType: FakeMessageType,
            reserved: Int,
            messageId: Long?,
        ) : this(chatRoom.roomId, messageType, reserved, messageId, false)

        private var message: String = ""
        private var originClass: Class<*>? = null
        private var originTag: String? = null

        fun j(message: String): b {
            this.message = message
            return this
        }

        fun l(
            sourceClass: Class<*>,
            tag: String,
        ): b {
            originClass = sourceClass
            originTag = tag
            return this
        }

        fun b(): FakeTextSendingLog {
            check(messageType == FakeMessageType.Text)
            return FakeTextSendingLog(roomId, message, originClass, originTag)
        }
    }
}

private class RenamedThreadedEntryMediaSender(
    chatRoom: FakeChatRoom,
    private val threadId: Long?,
    private val sendWithThread: () -> Boolean,
    private val attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    companion object {
        val sentUris = mutableListOf<String>()
        var lastType: FakeMessageType? = null
        var lastWriteType: FakeWriteType? = null

        fun reset() {
            sentUris.clear()
            lastType = null
            lastWriteType = null
        }
    }

    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId != null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(mediaItem.path.isNotBlank())
        check(!suppressAnimation)
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(!highQuality)
        check(listener == null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun q(
        uris: List<Any>,
        type: FakeMessageType,
        message: String,
        attachment: JSONObject,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        onSuccess: kotlin.jvm.functions.Function1<Any?, Any?>,
        onFailure: kotlin.jvm.functions.Function1<Any?, Any?>,
    ) {
        sentUris += uris.map { uri -> uri.toString().removePrefix("uri:") }
        lastType = type
        lastWriteType = writeType
        check(message.isEmpty())
        check(attachment.optString("callingPkg") == "com.kakao.talk")
        check(forwardExtra == null)
        check(writeType == FakeWriteType.Connect)
        check(!shareOriginal)
        check(!highQuality)
        assertEquals("ok", onSuccess.invoke("ok"))
        assertEquals(null, onFailure.invoke("ignored"))
    }
}

private class RenamedThreadedInjectMediaSender(
    chatRoom: FakeChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId == null || threadId >= 0L)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun z(
        sendingLog: Any,
        writeType: FakeWriteType,
        listener: FakeListener?,
    ) {
        check(sendingLog.hashCode() != Int.MIN_VALUE)
        check(writeType.name.isNotBlank())
        check(listener == null || listener.hashCode() != Int.MIN_VALUE)
    }
}

private class FakeThreadedRequestCompanion {
    @Suppress("UNUSED_PARAMETER")
    fun u(
        chatRoom: FakeChatRoomModel,
        sendingLog: Any,
        writeType: FakeWriteType,
        listener: FakeListener?,
        shouldRetry: Boolean,
    ) {
        check(chatRoom.roomId >= 0L)
        check(sendingLog.hashCode() != Int.MIN_VALUE)
        check(writeType.name.isNotBlank())
        check(listener == null || listener.hashCode() != Int.MIN_VALUE)
        check(!shouldRetry || shouldRetry)
    }
}

private object FakeChatRuntime {
    val resolvedRoomIds = mutableListOf<Long>()

    fun reset() {
        resolvedRoomIds.clear()
        FakeMasterDatabase.INSTANCE = FakeMasterDatabase()
    }
}

private class FakeMasterDatabase {
    companion object {
        @JvmField
        var INSTANCE: FakeMasterDatabase? = FakeMasterDatabase()
    }

    @Suppress("FunctionName")
    fun O(): FakeRoomDao = FakeRoomDao()
}

private class FakeRoomDao {
    fun h(roomId: Long): FakeRoomEntity = FakeRoomEntity(roomId)
}

private data class FakeRoomEntity(
    val roomId: Long,
)

private class FakeChatRoomModel private constructor(
    val roomId: Long,
) {
    companion object {
        @JvmField
        val CompanionResolver = Resolver()
    }

    class Resolver {
        fun c(entity: FakeRoomEntity): FakeChatRoomModel {
            FakeChatRuntime.resolvedRoomIds += entity.roomId
            return FakeChatRoomModel(entity.roomId)
        }
    }
}

private class LegacyNameSensitiveChatRoom private constructor(
    val roomId: Long,
) {
    companion object {
        @JvmField
        val CompanionResolver = Resolver()
    }

    class Resolver {
        fun c(entity: FakeRoomEntity): LegacyNameSensitiveChatRoom {
            LegacyNameSensitiveRecorder.calls += "c"
            return LegacyNameSensitiveChatRoom(entity.roomId)
        }

        fun z(entity: FakeRoomEntity): LegacyNameSensitiveChatRoom {
            LegacyNameSensitiveRecorder.calls += "z"
            return LegacyNameSensitiveChatRoom(entity.roomId)
        }
    }
}

private object LegacyNameSensitiveRecorder {
    val calls = mutableListOf<String>()
}

private class FakeChatRoomManager {
    companion object {
        @JvmStatic
        fun j(): FakeChatRoomManager = FakeChatRoomManager()
    }

    @Suppress("UNUSED_PARAMETER")
    fun e0(
        roomId: Long,
        includeMembers: Boolean,
        includeOpenLink: Boolean,
    ): FakeChatRoomModel? = null

    fun d0(roomId: Long): FakeChatRoomModel = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(roomId))
}

class KakaoClassRegistryTest {
    @Test
    fun `registry constructed with fake classes exposes all fields`() {
        val registry = buildFakeRegistry()

        assertEquals(FakeMediaSender::class.java, registry.chatMediaSenderClass)
        assertEquals(FakeMessageType::class.java, registry.messageTypeClass)
        assertEquals(FakeChatRoomManager::class.java, registry.chatRoomManagerClass)
        assertEquals(FakeMediaItem::class.java, registry.mediaItemClass)
        assertNotNull(registry.singleSendMethod)
        assertNotNull(registry.multiSendMethod)
        assertNotNull(registry.mediaItemConstructor)
        assertNotNull(registry.photoType)
        assertEquals(FakeMessageType.Photo, registry.photoType)
        assertNotNull(registry.multiPhotoType)
        assertEquals(FakeMessageType.MultiPhoto, registry.multiPhotoType)
        assertNotNull(registry.writeTypeNone)
        assertEquals(FakeWriteType.None, registry.writeTypeNone)
    }

    @Test
    fun `method selector prefers concrete candidate over abstract one`() {
        val method =
            KakaoClassRegistry.selectMethodCandidateForTest(
                label = "roomDao",
                candidates =
                    listOf(
                        AbstractRoomDaoContainer::class.java.getMethod("O"),
                        ConcreteRoomDaoContainer::class.java.getMethod("O"),
                    ),
            )

        assertEquals(ConcreteRoomDaoContainer::class.java, method.declaringClass)
    }

    @Test
    fun `method selector rejects ambiguous concrete candidates`() {
        val error =
            assertFailsWith<IllegalStateException> {
                KakaoClassRegistry.selectMethodCandidateForTest(
                    label = "ambiguous",
                    candidates =
                        listOf(
                            AmbiguousMethodOwner::class.java.getMethod("a", Long::class.javaPrimitiveType),
                            AmbiguousMethodOwner::class.java.getMethod("b", Long::class.javaPrimitiveType),
                        ),
                )
            }

        assertTrue(error.message?.contains("ambiguous") == true)
    }

    @Test
    fun `method selector prefers known method name when candidates are ambiguous`() {
        val method =
            KakaoClassRegistry.selectMethodCandidateForTest(
                label = "direct resolver",
                candidates =
                    listOf(
                        AmbiguousMethodOwner::class.java.getMethod("a", Long::class.javaPrimitiveType),
                        AmbiguousMethodOwner::class.java.getMethod("b", Long::class.javaPrimitiveType),
                    ),
                preferredNames = setOf("b"),
            )

        assertEquals("b", method.name)
    }

    @Test
    fun `chat media sender selector accepts concrete subclass inheriting send methods`() {
        val selected =
            KakaoClassRegistry.selectChatMediaSenderCandidateForTest(
                candidates =
                    listOf(
                        AbstractInheritedMediaSender::class.java,
                        ConcreteInheritedMediaSender::class.java,
                    ),
                mediaItemClass = FakeMediaItem::class.java,
                function0Class = kotlin.jvm.functions.Function0::class.java,
                function1Class = kotlin.jvm.functions.Function1::class.java,
            )

        assertEquals(ConcreteInheritedMediaSender::class.java, selected)
    }

    @Test
    fun `chat media sender selector rejects ambiguous concrete classes`() {
        val error =
            assertFailsWith<IllegalStateException> {
                KakaoClassRegistry.selectChatMediaSenderCandidateForTest(
                    candidates =
                        listOf(
                            ConcreteInheritedMediaSender::class.java,
                            AlternateConcreteInheritedMediaSender::class.java,
                        ),
                    mediaItemClass = FakeMediaItem::class.java,
                    function0Class = kotlin.jvm.functions.Function0::class.java,
                    function1Class = kotlin.jvm.functions.Function1::class.java,
                )
            }

        assertTrue(error.message?.contains("ambiguous") == true)
    }

    @Test
    fun `chat media sender method resolver accepts inherited methods`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = ConcreteInheritedMediaSender::class.java,
                mediaItemClass = FakeMediaItem::class.java,
                messageTypeClass = FakeMessageType::class.java,
            )

        assertEquals("n", methods.first.name)
        assertEquals("p", methods.second.name)
        assertEquals(AbstractInheritedMediaSender::class.java, methods.first.declaringClass)
        assertEquals(AbstractInheritedMediaSender::class.java, methods.second.declaringClass)
    }

    @Test
    fun `chat media sender method resolver accepts inherited non public methods`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = ConcreteProtectedInheritedMediaSender::class.java,
                mediaItemClass = FakeMediaItem::class.java,
                messageTypeClass = FakeMessageType::class.java,
            )

        assertEquals("n", methods.first.name)
        assertEquals("p", methods.second.name)
        assertEquals(AbstractProtectedInheritedMediaSender::class.java, methods.first.declaringClass)
        assertEquals(AbstractProtectedInheritedMediaSender::class.java, methods.second.declaringClass)
    }
}

private abstract class AbstractRoomDaoContainer {
    @Suppress("FunctionName")
    abstract fun O(): FakeRoomDao
}

private class ConcreteRoomDaoContainer : AbstractRoomDaoContainer() {
    @Suppress("FunctionName")
    override fun O(): FakeRoomDao = FakeRoomDao()
}

private class AmbiguousMethodOwner {
    fun a(roomId: Long): FakeRoomEntity = FakeRoomEntity(roomId)

    fun b(roomId: Long): FakeRoomEntity = FakeRoomEntity(roomId)
}

private open class FakeBaseChatRoom

private class FakeDerivedChatRoom : FakeBaseChatRoom()

private class FakePolymorphicMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    private val roomClassName = chatRoom.javaClass.name

    companion object {
        val sentPaths = mutableListOf<String>()

        fun reset() {
            sentPaths.clear()
        }
    }

    init {
        check(roomClassName.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        sentPaths += mediaItem.path
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        error("not used in this test")
    }
}

private class ExactPreferredMediaSender {
    companion object {
        var exactCalls = 0
        var baseCalls = 0

        fun reset() {
            exactCalls = 0
            baseCalls = 0
        }
    }

    constructor(
        chatRoom: FakeBaseChatRoom,
        threadId: Long?,
        sendWithThread: () -> Boolean,
        attachmentDecorator: (JSONObject) -> JSONObject?,
    ) {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
        baseCalls += 1
    }

    constructor(
        chatRoom: FakeDerivedChatRoom,
        threadId: Long?,
        sendWithThread: () -> Boolean,
        attachmentDecorator: (JSONObject) -> JSONObject?,
    ) {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
        exactCalls += 1
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        check(mediaItem.path.isNotBlank())
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(!highQuality)
        check(listener == null)
    }
}

private class PrimitiveThreadParamMediaSender(
    chatRoom: FakeChatRoom,
    threadId: Long,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    companion object {
        val sentPaths = mutableListOf<String>()

        fun reset() {
            sentPaths.clear()
        }
    }

    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId == 0L)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        sentPaths += mediaItem.path
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        error("not used in this test")
    }
}

private abstract class AbstractInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        check(mediaItem.path.isNotBlank())
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(!highQuality)
        check(listener == null)
    }
}

private class ConcreteInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) : AbstractInheritedMediaSender(chatRoom, threadId, sendWithThread, attachmentDecorator)

private class AlternateConcreteInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) : AbstractInheritedMediaSender(chatRoom, threadId, sendWithThread, attachmentDecorator)

private abstract class AbstractProtectedInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    protected fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        check(mediaItem.path.isNotBlank())
    }

    @Suppress("UNUSED_PARAMETER")
    protected fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(!highQuality)
        check(listener == null)
    }
}

private class ConcreteProtectedInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) : AbstractProtectedInheritedMediaSender(chatRoom, threadId, sendWithThread, attachmentDecorator)

private fun buildFakeRegistry(): KakaoClassRegistry {
    val singleSend =
        FakeMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakeMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = FakeMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

private fun buildPolymorphicRegistry(): KakaoClassRegistry {
    val singleSend =
        FakePolymorphicMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakePolymorphicMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = FakePolymorphicMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeBaseChatRoom::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

private fun buildExactPreferredRegistry(): KakaoClassRegistry {
    val singleSend =
        ExactPreferredMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        ExactPreferredMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = ExactPreferredMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeBaseChatRoom::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

private fun buildPrimitiveThreadParamRegistry(): KakaoClassRegistry {
    val singleSend =
        PrimitiveThreadParamMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        PrimitiveThreadParamMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = PrimitiveThreadParamMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

private fun buildLegacyNameSensitiveRegistry(): KakaoClassRegistry {
    val singleSend =
        FakeMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakeMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = FakeMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = LegacyNameSensitiveChatRoom::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

private fun buildRenamedThreadedRegistry(): KakaoClassRegistry {
    val singleSend =
        RenamedThreadedEntryMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        RenamedThreadedEntryMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = RenamedThreadedEntryMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}
