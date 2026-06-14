package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

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
    val requiresRequestId =
        admission.strictBool("requiresRequestId")
            ?: error("bridge core admission did not return request id policy")
    if (!requiresRequestId) return block()
    val dedupeKey =
        admission.string("dedupeKey")
            ?: error("bridge core admission did not return a request dedupe key")
    return deduper.execute(dedupeKey, request.requestId) { block() }
}
