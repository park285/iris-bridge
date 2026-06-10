package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.requestRequiresRequestId

internal inline fun executeWithBridgeAdmission(
    request: ImageBridgeProtocol.ImageBridgeRequest,
    metrics: BridgeMetrics,
    deduper: BridgeRequestDeduper,
    crossinline block: () -> ImageBridgeProtocol.ImageBridgeResponse,
): ImageBridgeProtocol.ImageBridgeResponse {
    if (!BridgeCore.requestRequiresRequestId(request.action)) return block()
    val admission = deduper.validateAdmission(request.action, request.requestId)
    if (!admission.isOk) {
        if (admission.errorCode == ImageBridgeProtocol.ERROR_MISSING_REQUEST_ID) {
            metrics.recordMissingRequestId()
        }
        return bridgeFailureResponse(
            error = admission.errorMessage ?: "request admission failed",
            errorCode = admission.errorCode ?: ImageBridgeProtocol.ERROR_BAD_REQUEST,
            requestId = request.requestId,
        )
    }
    val requestId = request.requestId?.takeIf { it.isNotBlank() } ?: return block()
    return deduper.execute("${request.action}:$requestId") { block() }
}
