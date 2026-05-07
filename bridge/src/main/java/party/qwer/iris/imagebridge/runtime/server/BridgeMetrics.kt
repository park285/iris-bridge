package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class BridgeMetrics {
    private val sendSuccess = AtomicLong()
    private val sendFailure = AtomicLong()
    private val pathValidationFailure = AtomicLong()
    private val unauthorizedClient = AtomicLong()
    private val bridgeBusy = AtomicLong()
    private val bridgeShuttingDown = AtomicLong()
    private val timeout = AtomicLong()
    private val missingRequestId = AtomicLong()
    private val rejectedClient = AtomicLong()
    private val activeClient = AtomicLong()
    private val queuedClient = AtomicLong()
    private val muxRequestCancelled = AtomicLong()
    private val muxRequestDeduplicated = AtomicLong()
    private val muxGoawaySent = AtomicLong()
    private val lastSendRequestId = AtomicReference<String?>()
    private val lastSendStartedAtEpochMs = AtomicReference<Long?>()
    private val lastSendCompletedAtEpochMs = AtomicReference<Long?>()
    private val lastSendDurationMs = AtomicReference<Long?>()
    private val lastSendErrorCode = AtomicReference<String?>()

    fun recordSendStart(
        requestId: String?,
        startedAtEpochMs: Long,
    ) {
        if (requestId == null) {
            missingRequestId.incrementAndGet()
        }
        lastSendRequestId.set(requestId)
        lastSendStartedAtEpochMs.set(startedAtEpochMs)
        lastSendCompletedAtEpochMs.set(null)
        lastSendDurationMs.set(null)
        lastSendErrorCode.set(null)
    }

    fun recordMissingRequestId() = missingRequestId.incrementAndGet()

    fun recordSendSuccess(
        completedAtEpochMs: Long,
        durationMs: Long,
    ) {
        sendSuccess.incrementAndGet()
        lastSendCompletedAtEpochMs.set(completedAtEpochMs)
        lastSendDurationMs.set(durationMs)
        lastSendErrorCode.set(null)
    }

    fun recordSendFailure(errorCode: String) {
        sendFailure.incrementAndGet()
        lastSendErrorCode.set(errorCode)
    }

    fun recordPathValidationFailure() = pathValidationFailure.incrementAndGet()

    fun recordUnauthorizedClient() = unauthorizedClient.incrementAndGet()

    fun recordBridgeBusy() {
        bridgeBusy.incrementAndGet()
        rejectedClient.incrementAndGet()
    }

    fun recordBridgeShuttingDown() {
        bridgeShuttingDown.incrementAndGet()
        rejectedClient.incrementAndGet()
    }

    fun recordTimeout() = timeout.incrementAndGet()

    fun recordClientStart() = activeClient.incrementAndGet()

    fun recordClientEnd() = activeClient.decrementAndGet()

    fun recordQueuedClientCount(count: Long) {
        queuedClient.set(count.coerceAtLeast(0))
    }

    fun recordMuxRequestCancelled() = muxRequestCancelled.incrementAndGet()

    fun recordMuxRequestDeduplicated() = muxRequestDeduplicated.incrementAndGet()

    fun recordMuxGoawaySent() = muxGoawaySent.incrementAndGet()

    fun snapshot(): ImageBridgeProtocol.ImageBridgeMetrics =
        ImageBridgeProtocol.ImageBridgeMetrics(
            sendSuccess = sendSuccess.get(),
            sendFailure = sendFailure.get(),
            pathValidationFailure = pathValidationFailure.get(),
            unauthorizedClient = unauthorizedClient.get(),
            bridgeBusy = bridgeBusy.get(),
            bridgeShuttingDown = bridgeShuttingDown.get(),
            timeout = timeout.get(),
            missingRequestId = missingRequestId.get(),
            rejectedClient = rejectedClient.get(),
            activeClient = activeClient.get(),
            queuedClient = queuedClient.get(),
            muxRequestCancelled = muxRequestCancelled.get(),
            muxRequestDeduplicated = muxRequestDeduplicated.get(),
            muxGoawaySent = muxGoawaySent.get(),
            lastSendRequestId = lastSendRequestId.get(),
            lastSendStartedAtEpochMs = lastSendStartedAtEpochMs.get(),
            lastSendCompletedAtEpochMs = lastSendCompletedAtEpochMs.get(),
            lastSendDurationMs = lastSendDurationMs.get(),
            lastSendErrorCode = lastSendErrorCode.get(),
        )
}
