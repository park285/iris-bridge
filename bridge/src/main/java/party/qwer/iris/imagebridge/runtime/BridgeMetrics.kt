package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.atomic.AtomicLong

internal class BridgeMetrics {
    private val sendSuccess = AtomicLong()
    private val sendFailure = AtomicLong()
    private val pathValidationFailure = AtomicLong()
    private val unauthorizedClient = AtomicLong()
    private val bridgeBusy = AtomicLong()
    private val bridgeShuttingDown = AtomicLong()
    private val timeout = AtomicLong()

    fun recordSendSuccess() {
        sendSuccess.incrementAndGet()
    }

    fun recordSendFailure() {
        sendFailure.incrementAndGet()
    }

    fun recordPathValidationFailure() {
        pathValidationFailure.incrementAndGet()
    }

    fun recordUnauthorizedClient() {
        unauthorizedClient.incrementAndGet()
    }

    fun recordBridgeBusy() {
        bridgeBusy.incrementAndGet()
    }

    fun recordBridgeShuttingDown() {
        bridgeShuttingDown.incrementAndGet()
    }

    fun recordTimeout() {
        timeout.incrementAndGet()
    }

    fun snapshot(): ImageBridgeProtocol.ImageBridgeMetrics =
        ImageBridgeProtocol.ImageBridgeMetrics(
            sendSuccess = sendSuccess.get(),
            sendFailure = sendFailure.get(),
            pathValidationFailure = pathValidationFailure.get(),
            unauthorizedClient = unauthorizedClient.get(),
            bridgeBusy = bridgeBusy.get(),
            bridgeShuttingDown = bridgeShuttingDown.get(),
            timeout = timeout.get(),
        )
}
