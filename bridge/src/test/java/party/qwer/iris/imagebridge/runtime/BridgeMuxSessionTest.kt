@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.server.BridgeMetrics
import party.qwer.iris.imagebridge.runtime.server.BridgeMuxSession
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val metrics = BridgeMetrics()

        BridgeMuxSession(
            client = socket,
            executor = DirectExecutorService(),
            handler = handler,
            isRunning = { true },
            metrics = metrics,
            logError = { _, _, _ -> },
        ).run()

        val response = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(socket.outputStream.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_RESPONSE, response.type)
        assertEquals("corr-1", response.correlationId)
        assertEquals(ImageBridgeProtocol.STATUS_OK, response.response?.status)
        assertEquals(1, metrics.muxSessionSnapshot().writeCount)
        assertTrue(metrics.muxSessionSnapshot().writeLatencyNanosTotal >= 0)
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
        assertEquals(1, metrics.muxSessionSnapshot().busyCount)
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
        assertEquals(1, metrics.muxSessionSnapshot().cancelCount)
        assertEquals(1, metrics.muxSessionSnapshot().lateResponseCount)
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

    @Test
    fun `mux session writer encodes outside write lock`() {
        val source =
            sourceFile(
                "src/main/java/party/qwer/iris/imagebridge/runtime/server/BridgeMuxSessionWriter.kt",
                "bridge/src/main/java/party/qwer/iris/imagebridge/runtime/server/BridgeMuxSessionWriter.kt",
            ).readText()
        val encodeIndex = source.indexOf("ImageBridgeMuxProtocol.encodeFrameBytes(frame)")
        val lockIndex = source.indexOf("synchronized(writeLock)")

        assertTrue(encodeIndex >= 0, "writer should pre-encode frame bytes")
        assertTrue(lockIndex >= 0, "writer should keep a write lock for frame ordering")
        assertTrue(encodeIndex < lockIndex, "JSON encoding must happen before taking writeLock")
        assertFalse(
            source.substring(lockIndex).contains("ImageBridgeMuxProtocol.writeFrame("),
            "writeLock scope should only write pre-encoded bytes",
        )
    }

    private fun sourceFile(
        moduleRelative: String,
        rootRelative: String,
    ): File = listOf(File(moduleRelative), File(rootRelative)).first { it.exists() }
}
