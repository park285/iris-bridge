package party.qwer.iris.imagebridge.runtime.core

import party.qwer.iris.ImageBridgeProtocol

fun BridgeCore.requestRequiresRequestId(action: String): Boolean = requestRequiresRequestId(action, ::nativeRequestRequiresRequestId)

internal fun BridgeCore.requestRequiresRequestId(
    action: String,
    requestIdPolicy: (String) -> Boolean?,
): Boolean =
    requestIdPolicy(action)
        ?: error("bridge core unavailable to resolve request id requirement")

private fun nativeRequestRequiresRequestId(action: String): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniRequest.nativeRequestRequiresRequestId(action) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core request admission threw", error)
            null
        }
}

fun BridgeCore.requestDedupeKey(
    action: String,
    requestId: String?,
): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniRequest.nativeRequestDedupeKey(action, requestId) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core request dedupe key policy threw", error)
            null
        }
}

fun BridgeCore.classifyErrorCode(
    message: String,
    isIllegalArgument: Boolean,
): String {
    if (!bridgeCoreLoadLibraryOnce()) return ImageBridgeProtocol.ERROR_INTERNAL
    return runCatching {
        val envelope =
            BridgeCoreEnvelope.parse(
                BridgeCoreJniRequest.nativeClassifyErrorCode(message, isIllegalArgument),
            )
        if (envelope.isOk) {
            envelope.string("classifiedErrorCode") ?: ImageBridgeProtocol.ERROR_INTERNAL
        } else {
            ImageBridgeProtocol.ERROR_INTERNAL
        }
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core error classification threw", error)
        ImageBridgeProtocol.ERROR_INTERNAL
    }
}
