package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import party.qwer.iris.FrameReadStage
import party.qwer.iris.FrameReadTimeoutException
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.ConcurrentHashMap
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
    private val activeRequests = ConcurrentHashMap<String, ActiveMuxRequest>()
    private val writeLock = Any()
    private val writer = BridgeMuxSessionWriter(client, writeLock, ::close)

    fun run() {
        metrics.recordClientStart()
        try {
            while (isRunning() && !closed.get()) {
                val frame = ImageBridgeMuxProtocol.readFrame(client.inputStream)
                when (frame.type) {
                    ImageBridgeMuxProtocol.TYPE_REQUEST -> dispatchRequest(frame)
                    ImageBridgeMuxProtocol.TYPE_PING ->
                        writer.writeFrame(
                            ImageBridgeMuxFrame(
                                type = ImageBridgeMuxProtocol.TYPE_PONG,
                                correlationId = frame.correlationId,
                            ),
                        )
                    ImageBridgeMuxProtocol.TYPE_CANCEL -> handleCancel(frame.correlationId)
                    else -> writer.writeProtocolFailure(frame.correlationId, "unsupported mux frame type: ${frame.type}")
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
            writer.writeProtocolFailure(correlationId, "malformed mux request frame")
            return
        }
        if (inFlight.incrementAndGet() > maxInFlight) {
            inFlight.decrementAndGet()
            metrics.recordBridgeBusy()
            writer.writeResponse(
                correlationId,
                bridgeFailureResponse(
                    error = "bridge busy",
                    errorCode = ImageBridgeProtocol.ERROR_BRIDGE_BUSY,
                    requestId = request.requestId,
                ),
            )
            return
        }
        val active = ActiveMuxRequest(correlationId, request)
        activeRequests[correlationId] = active
        try {
            executor.execute {
                handleAcceptedRequest(active)
            }
        } catch (error: RejectedExecutionException) {
            inFlight.decrementAndGet()
            activeRequests.remove(correlationId)
            metrics.recordBridgeBusy()
            writer.writeResponse(
                correlationId,
                bridgeFailureResponse(
                    error = "bridge busy",
                    errorCode = ImageBridgeProtocol.ERROR_BRIDGE_BUSY,
                    requestId = request.requestId,
                ),
            )
        }
    }

    private fun handleAcceptedRequest(active: ActiveMuxRequest) {
        try {
            if (!active.cancelled.get()) {
                writeResponseOrClose(active, handleRequestSafely(active.request))
            }
        } finally {
            activeRequests.remove(active.correlationId)
            inFlight.decrementAndGet()
        }
    }

    private fun handleCancel(correlationId: String?) {
        if (correlationId.isNullOrBlank()) return
        val active = activeRequests[correlationId] ?: return
        active.cancelled.set(true)
        metrics.recordMuxRequestCancelled()
    }

    private fun handleRequestSafely(
        request: ImageBridgeProtocol.ImageBridgeRequest,
    ): ImageBridgeProtocol.ImageBridgeResponse =
        try {
            handler.handle(request)
        } catch (error: Exception) {
            bridgeFailureResponse(
                error = error.message ?: "internal error",
                errorCode = ImageBridgeProtocol.ERROR_INTERNAL,
                requestId = request.requestId,
            )
        }

    private fun writeResponseOrClose(
        active: ActiveMuxRequest,
        response: ImageBridgeProtocol.ImageBridgeResponse,
    ) {
        if (closed.get() || active.cancelled.get()) return
        try {
            writer.writeResponse(active.correlationId, response)
        } catch (error: Exception) {
            if (!closed.get() && isRunning()) {
                logError(TAG, "bridge mux response write failed", error)
            }
            close()
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

private data class ActiveMuxRequest(
    val correlationId: String,
    val request: ImageBridgeProtocol.ImageBridgeRequest,
    val cancelled: AtomicBoolean = AtomicBoolean(false),
)
