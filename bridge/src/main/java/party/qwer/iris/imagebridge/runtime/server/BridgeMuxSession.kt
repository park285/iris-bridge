package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import party.qwer.iris.FrameReadStage
import party.qwer.iris.FrameReadTimeoutException
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class BridgeMuxSession(
    private val client: BridgeMuxSocket,
    private val executor: ExecutorService,
    private val handler: ImageBridgeRequestHandler,
    private val isRunning: () -> Boolean,
    private val metrics: BridgeMetrics,
    private val maxInFlight: Int = DEFAULT_MAX_IN_FLIGHT,
    private val logError: (String, String, Throwable) -> Unit = { tag, message, error -> Log.e(tag, message, error) },
) {
    private val closed = AtomicBoolean(false)
    private val inFlight = AtomicInteger(0)
    private val writeLock = Any()

    fun run() {
        metrics.recordClientStart()
        try {
            while (isRunning() && !closed.get()) {
                val frame = ImageBridgeMuxProtocol.readFrame(client.inputStream)
                when (frame.type) {
                    ImageBridgeMuxProtocol.TYPE_REQUEST -> dispatchRequest(frame)
                    ImageBridgeMuxProtocol.TYPE_PING ->
                        writeFrame(
                            ImageBridgeMuxFrame(
                                type = ImageBridgeMuxProtocol.TYPE_PONG,
                                correlationId = frame.correlationId,
                            ),
                        )
                    ImageBridgeMuxProtocol.TYPE_CANCEL -> Unit
                    else -> writeProtocolFailure(frame.correlationId, "unsupported mux frame type: ${frame.type}")
                }
            }
        } catch (error: Exception) {
            if (!closed.get() && isRunning() && !error.isMuxIdleTimeout()) {
                logError(TAG, "bridge mux session failed", error)
            }
        } finally {
            metrics.recordClientEnd()
            close()
        }
    }

    private fun dispatchRequest(frame: ImageBridgeMuxFrame) {
        val correlationId = frame.correlationId
        val request = frame.request
        if (correlationId.isNullOrBlank() || request == null) {
            writeProtocolFailure(correlationId, "malformed mux request frame")
            return
        }
        if (inFlight.incrementAndGet() > maxInFlight) {
            inFlight.decrementAndGet()
            metrics.recordBridgeBusy()
            writeResponse(
                correlationId,
                bridgeFailureResponse(
                    error = "bridge busy",
                    errorCode = ImageBridgeProtocol.ERROR_BRIDGE_BUSY,
                    requestId = request.requestId,
                ),
            )
            return
        }
        try {
            executor.execute {
                try {
                    writeResponse(correlationId, handler.handle(request))
                } catch (error: Exception) {
                    writeResponse(
                        correlationId,
                        bridgeFailureResponse(
                            error = error.message ?: "internal error",
                            errorCode = ImageBridgeProtocol.ERROR_INTERNAL,
                            requestId = request.requestId,
                        ),
                    )
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        } catch (error: RejectedExecutionException) {
            inFlight.decrementAndGet()
            metrics.recordBridgeBusy()
            writeResponse(
                correlationId,
                bridgeFailureResponse(
                    error = "bridge busy",
                    errorCode = ImageBridgeProtocol.ERROR_BRIDGE_BUSY,
                    requestId = request.requestId,
                ),
            )
        }
    }

    private fun writeResponse(
        correlationId: String,
        response: ImageBridgeProtocol.ImageBridgeResponse,
    ) {
        writeFrame(
            ImageBridgeMuxFrame(
                type = ImageBridgeMuxProtocol.TYPE_RESPONSE,
                correlationId = correlationId,
                response = response,
            ),
        )
    }

    private fun writeProtocolFailure(
        correlationId: String?,
        message: String,
    ) {
        if (correlationId.isNullOrBlank()) {
            close()
            return
        }
        writeResponse(
            correlationId,
            bridgeFailureResponse(
                error = message,
                errorCode = ImageBridgeProtocol.ERROR_BAD_REQUEST,
            ),
        )
    }

    private fun writeFrame(frame: ImageBridgeMuxFrame) {
        synchronized(writeLock) {
            ImageBridgeMuxProtocol.writeFrame(client.outputStream, frame)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { client.close() }
    }

    private fun Throwable.isMuxIdleTimeout(): Boolean = this is FrameReadTimeoutException && stage == FrameReadStage.LENGTH && bytesRead == 0

    private companion object {
        private const val TAG = "IrisBridge"
        private const val DEFAULT_MAX_IN_FLIGHT = 4
    }
}
