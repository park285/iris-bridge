package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.classifyErrorCode

internal const val BRIDGE_LOG_TAG = "IrisBridge"

internal fun BridgeMetrics.recordFailure(errorCode: String) {
    when (errorCode) {
        ImageBridgeProtocol.ERROR_PATH_VALIDATION -> recordPathValidationFailure()
        ImageBridgeProtocol.ERROR_UNAUTHORIZED -> recordUnauthorizedClient()
        ImageBridgeProtocol.ERROR_TIMEOUT -> recordTimeout()
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
