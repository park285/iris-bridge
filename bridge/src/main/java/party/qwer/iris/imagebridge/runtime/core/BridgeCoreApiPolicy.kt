package party.qwer.iris.imagebridge.runtime.core

import party.qwer.iris.ImageBridgeProtocol

fun BridgeCore.mentionsHashFromJson(mentionsJson: String?): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniReply.nativeMentionsHashFromJson(mentionsJson) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core mentions hash threw", error)
            null
        }
}

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

fun BridgeCore.isTruthyFlag(raw: String): Boolean = isTruthyFlag(raw, ::nativeIsTruthyFlag)

internal fun BridgeCore.isTruthyFlag(
    raw: String,
    flagPolicy: (String) -> Boolean?,
): Boolean =
    flagPolicy(raw)
        ?: error("bridge core unavailable to parse bridge flag")

private fun nativeIsTruthyFlag(raw: String): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniPolicy.nativeIsTruthyFlag(raw) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core flag parsing threw", error)
            null
        }
}

fun BridgeCore.normalizeSecurityMode(raw: String?): String = normalizeSecurityMode(raw, ::nativeNormalizeSecurityMode)

internal fun BridgeCore.normalizeSecurityMode(
    raw: String?,
    modePolicy: (String?) -> String?,
): String =
    modePolicy(raw)
        ?: error("bridge core unavailable to normalize security mode")

private fun nativeNormalizeSecurityMode(raw: String?): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniPolicy.nativeNormalizeSecurityMode(raw) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core security mode normalization threw", error)
            null
        }
}

fun BridgeCore.allowedPeerUids(
    securityModeRaw: String?,
    extraUidsRaw: String?,
): IntArray = allowedPeerUids(securityModeRaw, extraUidsRaw, ::nativeAllowedPeerUids)

internal fun BridgeCore.allowedPeerUids(
    securityModeRaw: String?,
    extraUidsRaw: String?,
    peerUidPolicy: (String?, String?) -> IntArray?,
): IntArray =
    peerUidPolicy(securityModeRaw, extraUidsRaw)
        ?: error("bridge core unavailable to resolve allowed peer uids")

private fun nativeAllowedPeerUids(
    securityModeRaw: String?,
    extraUidsRaw: String?,
): IntArray? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniPolicy.nativeAllowedPeerUids(securityModeRaw, extraUidsRaw) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core peer uid policy threw", error)
            null
        }
}

fun BridgeCore.currentBridgeCapabilities(
    registryAvailable: Boolean,
    registryError: String?,
    specReady: Boolean,
    textSupported: Boolean,
    textReady: Boolean,
    textReason: String?,
    sendTextEnabled: Boolean,
    sendMarkdownEnabled: Boolean,
): BridgeCoreEnvelope? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreEnvelope.parse(
            BridgeCoreJniPolicy.nativeCurrentBridgeCapabilities(
                registryAvailable,
                registryError,
                specReady,
                textSupported,
                textReady,
                textReason,
                sendTextEnabled,
                sendMarkdownEnabled,
            ),
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core capabilities policy threw", error)
        null
    }
}

internal fun BridgeCore.sendBlockReasonRaw(
    installAttempted: Boolean,
    hookNames: Array<String>,
    hookInstalled: BooleanArray,
    imageCount: Int,
    threadId: Long?,
    threadScope: Int?,
): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniPolicy.nativeSendBlockReason(
            installAttempted,
            hookNames,
            hookInstalled,
            imageCount,
            threadId ?: 0L,
            threadId != null,
            threadScope ?: 0,
            threadScope != null,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core send block policy threw", error)
        null
    }
}

fun BridgeCore.sendBlockReason(
    installAttempted: Boolean,
    hookNames: Array<String>,
    hookInstalled: BooleanArray,
    imageCount: Int,
    threadId: Long?,
    threadScope: Int?,
): String? =
    sendBlockReasonRaw(
        installAttempted,
        hookNames,
        hookInstalled,
        imageCount,
        threadId,
        threadScope,
    )?.ifEmpty { null }

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

fun BridgeCore.mentionsHashFromAttachment(attachmentText: String?): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniReply.nativeMentionsHashFromAttachment(attachmentText) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core mentions hash threw", error)
            null
        }
}
