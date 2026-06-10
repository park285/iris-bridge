@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

class BridgeErrorClassifierTest {
    @Test
    fun `bridgeErrorCodeFor returns native bridge protocol classifications`() {
        val cases =
            listOf(
                IllegalStateException("unsupported protocol version") to ImageBridgeProtocol.ERROR_UNSUPPORTED_PROTOCOL,
                IllegalStateException("unauthorized bridge token") to ImageBridgeProtocol.ERROR_UNAUTHORIZED,
                IllegalStateException("image path validation timed out") to ImageBridgeProtocol.ERROR_PATH_VALIDATION,
                IllegalStateException("CHATROOM OPEN DISPATCH TIMED OUT") to ImageBridgeProtocol.ERROR_TIMEOUT,
                IllegalArgumentException() to ImageBridgeProtocol.ERROR_BAD_REQUEST,
                IllegalStateException("send failed") to ImageBridgeProtocol.ERROR_SEND_FAILED,
                Exception() to ImageBridgeProtocol.ERROR_SEND_FAILED,
            )

        for ((error, expected) in cases) {
            assertEquals(expected, bridgeErrorCodeFor(error), error.message)
        }
    }

    @Test
    fun `failure response records metrics from native error classification`() {
        val request =
            ImageBridgeProtocol.ImageBridgeRequest(
                action = ImageBridgeProtocol.ACTION_SEND_TEXT,
                protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                roomId = 1L,
                requestId = "req-1",
            )

        val unauthorizedMetrics = BridgeMetrics()
        val unauthorized =
            bridgeRequestFailureResponse(
                request = request,
                error = IllegalStateException("unauthorized bridge token"),
                metrics = unauthorizedMetrics,
                logError = { _, _, _ -> },
            )
        assertEquals(ImageBridgeProtocol.ERROR_UNAUTHORIZED, unauthorized.errorCode)
        assertEquals(1, unauthorizedMetrics.snapshot().unauthorizedClient)
        assertEquals(0, unauthorizedMetrics.snapshot().sendFailure)

        val timeoutMetrics = BridgeMetrics()
        val timeout =
            bridgeRequestFailureResponse(
                request = request,
                error = IllegalStateException("chatroom open dispatch timed out"),
                metrics = timeoutMetrics,
                logError = { _, _, _ -> },
            )
        assertEquals(ImageBridgeProtocol.ERROR_TIMEOUT, timeout.errorCode)
        assertEquals(1, timeoutMetrics.snapshot().timeout)
        assertEquals(0, timeoutMetrics.snapshot().sendFailure)

        val sendFailureMetrics = BridgeMetrics()
        val sendFailure =
            bridgeRequestFailureResponse(
                request = request,
                error = IllegalStateException("send failed"),
                metrics = sendFailureMetrics,
                logError = { _, _, _ -> },
            )
        assertEquals(ImageBridgeProtocol.ERROR_SEND_FAILED, sendFailure.errorCode)
        assertEquals(1, sendFailureMetrics.snapshot().sendFailure)
        assertEquals(ImageBridgeProtocol.ERROR_SEND_FAILED, sendFailureMetrics.snapshot().lastSendErrorCode)
    }

    @Test
    fun `recordFailure uses native metric bucket policy`() {
        val metrics = BridgeMetrics()

        metrics.recordFailure(
            ImageBridgeProtocol.ERROR_PATH_VALIDATION,
            failureMetricBucket = { "sendFailure" },
        )

        assertEquals(0, metrics.snapshot().pathValidationFailure)
        assertEquals(1, metrics.snapshot().sendFailure)
        assertEquals(ImageBridgeProtocol.ERROR_PATH_VALIDATION, metrics.snapshot().lastSendErrorCode)
    }
}
