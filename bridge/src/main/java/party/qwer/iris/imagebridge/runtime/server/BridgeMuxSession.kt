package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import party.qwer.iris.FrameReadStage
import party.qwer.iris.FrameReadTimeoutException
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreMuxCommand
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreMuxSession
import party.qwer.iris.imagebridge.runtime.core.muxCommand
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal const val BRIDGE_MUX_DEFAULT_MAX_IN_FLIGHT = 4

internal class BridgeMuxSession(
    private val client: BridgeMuxSocket,
    private val muxSession: BridgeCoreMuxSession,
    private val executor: ExecutorService,
    private val handler: ImageBridgeRequestHandler,
    private val isRunning: () -> Boolean,
    private val metrics: BridgeMetrics,
    private val logError: (String, String, Throwable) -> Unit = { tag, message, error -> Log.e(tag, message, error) },
) {
    private val closed = AtomicBoolean(false)
    private val activeRequests = ConcurrentHashMap<String, ActiveMuxRequest>()
    private val writeLock = Any()
    private val writer = BridgeMuxSessionWriter(client, writeLock, ::close, metrics)

    fun run() {
        metrics.recordClientStart()
        try {
            while (isRunning() && !closed.get()) {
                val frame = ImageBridgeMuxProtocol.readFrame(client.inputStream)
                val command = muxSession.onFrame(frame.muxSummaryJson()).muxCommand()
                handleMuxCommand(frame, command)
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

    private fun handleMuxCommand(
        frame: ImageBridgeMuxFrame,
        command: BridgeCoreMuxCommand?,
    ) {
        when (command) {
            is BridgeCoreMuxCommand.Dispatch -> dispatchRequest(frame, command.correlationId)
            is BridgeCoreMuxCommand.WritePong ->
                writer.writeFrame(
                    ImageBridgeMuxFrame(
                        type = ImageBridgeMuxProtocol.TYPE_PONG,
                        correlationId = command.correlationId,
                    ),
                )
            is BridgeCoreMuxCommand.WriteBadRequest -> writer.writeProtocolFailure(command.correlationId, command.message)
            is BridgeCoreMuxCommand.WriteBusy -> writeBusyResponse(command.correlationId, frame.request?.requestId)
            is BridgeCoreMuxCommand.MarkCancelled -> handleCancel(command.correlationId)
            BridgeCoreMuxCommand.Close -> close()
            BridgeCoreMuxCommand.Ignore -> Unit
            null -> closeForMuxStateMismatch("bridge mux core returned invalid command")
        }
    }

    private fun dispatchRequest(
        frame: ImageBridgeMuxFrame,
        correlationId: String,
    ) {
        val request = frame.request
        if (request == null || frame.correlationId != correlationId) {
            closeForMuxStateMismatch("bridge mux request state diverged")
            return
        }
        val active = ActiveMuxRequest(correlationId, request)
        if (activeRequests.putIfAbsent(correlationId, active) != null) {
            closeForMuxStateMismatch("bridge mux duplicate active request escaped core")
            return
        }
        try {
            executor.execute {
                handleAcceptedRequest(active)
            }
        } catch (error: RejectedExecutionException) {
            activeRequests.remove(correlationId)
            handleExecutorRejected(correlationId, request.requestId)
        }
    }

    private fun handleAcceptedRequest(active: ActiveMuxRequest) {
        try {
            if (active.cancelled.get()) {
                metrics.recordMuxLateResponse()
                return
            }
            writeResponseOrClose(active, handleRequestSafely(active.request))
        } finally {
            val completed = muxSession.onRequestCompleted(active.correlationId)
            if (!completed.isOk && !closed.get()) {
                closeForMuxStateMismatch("bridge mux completion rejected by core")
            }
            activeRequests.remove(active.correlationId)
        }
    }

    private fun handleCancel(correlationId: String) {
        val active =
            activeRequests[correlationId]
                ?: return closeForMuxStateMismatch("bridge mux cancel state diverged")
        active.cancelled.set(true)
        metrics.recordMuxRequestCancelled()
    }

    private fun handleExecutorRejected(
        correlationId: String,
        requestId: String?,
    ) {
        when (val command = muxSession.onExecutorRejected(correlationId).muxCommand()) {
            is BridgeCoreMuxCommand.WriteBusy -> writeBusyResponse(command.correlationId, requestId)
            BridgeCoreMuxCommand.Ignore -> Unit
            else -> closeForMuxStateMismatch("bridge mux executor rejection diverged")
        }
    }

    private fun writeBusyResponse(
        correlationId: String,
        requestId: String?,
    ) {
        metrics.recordBridgeBusy()
        writer.writeResponse(
            correlationId,
            bridgeFailureResponse(
                error = "bridge busy",
                errorCode = ImageBridgeProtocol.ERROR_BRIDGE_BUSY,
                requestId = requestId,
            ),
        )
    }

    private fun handleRequestSafely(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse =
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
        if (closed.get() || active.cancelled.get()) {
            metrics.recordMuxLateResponse()
            return
        }
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
        runCatching { muxSession.close() }
        runCatching { client.close() }
    }

    private fun closeForMuxStateMismatch(message: String) {
        if (!closed.get() && isRunning()) {
            logError(TAG, message, IllegalStateException(message))
        }
        close()
    }

    private fun Throwable.isMuxIdleTimeout(): Boolean =
        this is FrameReadTimeoutException && stage == FrameReadStage.LENGTH && bytesRead == 0

    private companion object {
        private const val TAG = "IrisBridge"
    }
}

private data class ActiveMuxRequest(
    val correlationId: String,
    val request: ImageBridgeProtocol.ImageBridgeRequest,
    val cancelled: AtomicBoolean = AtomicBoolean(false),
)
