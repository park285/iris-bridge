package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.replyHookSign(
    bridgeToken: String,
    roomId: Long,
    messageText: String,
    sessionId: String,
    createdAtEpochMs: Long,
    mentionsHash: String?,
): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniReply.nativeReplyHookSign(
            bridgeToken,
            roomId,
            messageText,
            sessionId,
            createdAtEpochMs,
            mentionsHash,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core reply-hook sign threw", error)
        null
    }
}

fun BridgeCore.replyHookVerify(
    bridgeToken: String,
    roomId: Long,
    messageText: String,
    sessionId: String?,
    createdAtEpochMs: Long?,
    mentionsHash: String?,
    signature: String?,
    nowEpochMs: Long,
): Boolean {
    if (createdAtEpochMs == null) return false
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching {
        BridgeCoreJniReply.nativeReplyHookVerify(
            bridgeToken,
            roomId,
            messageText,
            sessionId,
            createdAtEpochMs,
            mentionsHash,
            signature,
            nowEpochMs,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core reply-hook verify threw", error)
        false
    }
}

internal fun bridgeCoreLoadCompatibleLibraryOnce(): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    val abiVersion = runCatching { BridgeCoreJniContext.nativeAbiVersion() }.getOrNull()
    if (abiVersion != BridgeCore.EXPECTED_ABI_VERSION) {
        bridgeCoreLogError("bridge-core ABI mismatch: expected ${BridgeCore.EXPECTED_ABI_VERSION}, got $abiVersion")
        return false
    }
    return true
}

fun BridgeCore.loadOrNull(
    securityMode: String?,
    bridgeToken: String,
    requireHandshakeRaw: String?,
): BridgeCoreRuntime? {
    if (!bridgeCoreLoadCompatibleLibraryOnce()) return null
    val handle =
        runCatching { BridgeCoreJniContext.nativeCreateContext(securityMode, bridgeToken, requireHandshakeRaw) }
            .getOrElse { error ->
                bridgeCoreLogError("bridge-core context creation threw", error)
                return null
            }
    if (handle == 0L) {
        bridgeCoreLogError("bridge-core context creation returned null handle")
        return null
    }
    val requireHandshake = runCatching { BridgeCoreJniContext.nativeRequireHandshake(handle) }.getOrDefault(true)
    bridgeCoreLogInfo("bridge-core abi=$EXPECTED_ABI_VERSION loaded")
    return BridgeCoreRuntime(handle = handle, requireHandshake = requireHandshake)
}
