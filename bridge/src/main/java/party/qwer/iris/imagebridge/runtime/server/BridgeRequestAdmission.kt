package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.requestDedupeKey
import party.qwer.iris.imagebridge.runtime.core.requestRequiresRequestId

internal inline fun executeWithBridgeAdmission(
    request: ImageBridgeProtocol.ImageBridgeRequest,
    metrics: BridgeMetrics,
    deduper: BridgeRequestDeduper,
    crossinline block: () -> ImageBridgeProtocol.ImageBridgeResponse,
): ImageBridgeProtocol.ImageBridgeResponse {
    // Rust admission이 known action의 기준이다. read-only action은 통과하고,
    // unknown action은 Kotlin handler 분기 전에 fail-closed 된다.
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
    if (!BridgeCore.requestRequiresRequestId(request.action)) return block()
    val dedupeKey =
        BridgeCore.requestDedupeKey(request.action, request.requestId)
            ?: error("bridge core unavailable to build request dedupe key")
    return deduper.execute(dedupeKey, request.requestId) { block() }
}
