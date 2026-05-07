package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

internal inline fun executeWithBridgeAdmission(
    request: ImageBridgeProtocol.ImageBridgeRequest,
    metrics: BridgeMetrics,
    deduper: BridgeRequestDeduper,
    crossinline block: () -> ImageBridgeProtocol.ImageBridgeResponse,
): ImageBridgeProtocol.ImageBridgeResponse {
    if (!request.action.requiresBridgeRequestId()) return block()
    val requestId =
        request.requestId?.takeIf { it.isNotBlank() }
            ?: run {
                metrics.recordMissingRequestId()
                return bridgeFailureResponse(
                    error = "requestId missing",
                    errorCode = ImageBridgeProtocol.ERROR_MISSING_REQUEST_ID,
                    requestId = request.requestId,
                )
            }
    return deduper.execute("${request.action}:$requestId") { block() }
}
