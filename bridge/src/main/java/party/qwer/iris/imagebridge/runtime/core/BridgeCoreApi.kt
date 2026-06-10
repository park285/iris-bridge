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

fun BridgeCore.loadOrNull(
    securityMode: String?,
    bridgeToken: String,
    requireHandshakeRaw: String?,
): BridgeCoreRuntime? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    val abiVersion = runCatching { BridgeCoreJniContext.nativeAbiVersion() }.getOrNull()
    if (abiVersion != EXPECTED_ABI_VERSION) {
        bridgeCoreLogError("bridge-core ABI mismatch: expected $EXPECTED_ABI_VERSION, got $abiVersion")
        return null
    }
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
    bridgeCoreLogInfo("bridge-core abi=$abiVersion loaded")
    return BridgeCoreRuntime(handle = handle, requireHandshake = requireHandshake)
}
