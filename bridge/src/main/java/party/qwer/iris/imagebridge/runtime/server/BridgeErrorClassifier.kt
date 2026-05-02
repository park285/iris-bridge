package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

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
    return when {
        message == "unsupported protocol version" -> ImageBridgeProtocol.ERROR_UNSUPPORTED_PROTOCOL
        message == "unauthorized bridge token" || message == "bridge token must be configured in production mode" ->
            ImageBridgeProtocol.ERROR_UNAUTHORIZED
        message.contains("image path") ||
            message.startsWith("image file not found") ||
            message == "no image paths" ||
            message == "blank image path" ||
            message.startsWith("too many image paths") ->
            ImageBridgeProtocol.ERROR_PATH_VALIDATION
        message.contains("timed out", ignoreCase = true) -> ImageBridgeProtocol.ERROR_TIMEOUT
        message.endsWith("missing") ||
            message.contains("unavailable") ||
            error is IllegalArgumentException ->
            ImageBridgeProtocol.ERROR_BAD_REQUEST
        else -> ImageBridgeProtocol.ERROR_SEND_FAILED
    }
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
