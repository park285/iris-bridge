package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

internal fun bridgeRequestFailureResponse(
    request: ImageBridgeProtocol.ImageBridgeRequest,
    error: Exception,
    metrics: BridgeMetrics,
    logError: (String, String, Throwable) -> Unit,
): ImageBridgeProtocol.ImageBridgeResponse {
    val errorCode = bridgeErrorCodeFor(error)
    metrics.recordFailure(errorCode)
    val action = request.action.ifBlank { "<missing>" }
    val roomId = request.roomId?.toString() ?: "<missing>"
    val requestId = request.requestId ?: "<missing>"
    runCatching {
        logError(BRIDGE_LOG_TAG, "request handling failed action=$action roomId=$roomId requestId=$requestId", error)
    }
    return bridgeFailureResponse(
        error = error.message ?: "internal error",
        errorCode = errorCode,
        requestId = request.requestId,
    )
}
