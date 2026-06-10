package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.classifyErrorCode
import party.qwer.iris.imagebridge.runtime.core.failureMetricBucket

internal const val BRIDGE_LOG_TAG = "IrisBridge"

private const val FAILURE_METRIC_BUCKET_PATH_VALIDATION = "pathValidationFailure"
private const val FAILURE_METRIC_BUCKET_UNAUTHORIZED = "unauthorizedClient"
private const val FAILURE_METRIC_BUCKET_TIMEOUT = "timeout"

internal fun BridgeMetrics.recordFailure(
    errorCode: String,
    failureMetricBucket: (String) -> String = BridgeCore::failureMetricBucket,
) {
    when (failureMetricBucket(errorCode)) {
        FAILURE_METRIC_BUCKET_PATH_VALIDATION -> recordPathValidationFailure()
        FAILURE_METRIC_BUCKET_UNAUTHORIZED -> recordUnauthorizedClient()
        FAILURE_METRIC_BUCKET_TIMEOUT -> recordTimeout()
        else -> recordSendFailure(errorCode)
    }
}

internal fun bridgeErrorCodeFor(error: Exception): String {
    val message = error.message.orEmpty()
    return BridgeCore.classifyErrorCode(message, error is IllegalArgumentException)
}

internal fun bridgeFailureResponse(
    error: String,
    errorCode: String? = null,
    requestId: String? = null,
): ImageBridgeProtocol.ImageBridgeResponse =
    ImageBridgeProtocol.buildFailureResponse(
        error = error,
        errorCode = errorCode,
        requestId = requestId,
    )
