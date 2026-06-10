package party.qwer.iris.imagebridge.runtime.core

import android.util.Log

private const val TAG = "IrisBridge"
private const val LIBRARY_NAME = "iris_bridge_core"

internal fun bridgeCoreLogInfo(message: String) {
    runCatching { Log.i(TAG, message) }
}

internal fun bridgeCoreLogWarn(
    message: String,
    error: Throwable,
) {
    runCatching { Log.w(TAG, message, error) }
}

internal fun bridgeCoreLogError(message: String) {
    runCatching { Log.e(TAG, message) }
}

internal fun bridgeCoreLogError(
    message: String,
    error: Throwable,
) {
    runCatching { Log.e(TAG, message, error) }
}

internal fun bridgeCoreLoadLibraryOnce(): Boolean =
    runCatching { System.loadLibrary(LIBRARY_NAME) }
        .onFailure { bridgeCoreLogError("bridge-core load failed", it) }
        .isSuccess

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
        nativeReplyHookSign(
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

fun BridgeCore.mentionsHashFromJson(mentionsJson: String?): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { nativeMentionsHashFromJson(mentionsJson) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core mentions hash threw", error)
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
        nativeReplyHookVerify(
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

fun BridgeCore.mentionsHashFromAttachment(attachmentText: String?): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { nativeMentionsHashFromAttachment(attachmentText) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core mentions hash threw", error)
            null
        }
}

fun BridgeCore.loadOrNull(
    securityMode: String?,
    bridgeToken: String,
    requireHandshakeRaw: String?,
): BridgeCoreRuntime? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    val abiVersion = runCatching { nativeAbiVersion() }.getOrNull()
    if (abiVersion != EXPECTED_ABI_VERSION) {
        bridgeCoreLogError("bridge-core ABI mismatch: expected $EXPECTED_ABI_VERSION, got $abiVersion")
        return null
    }
    val handle =
        runCatching { nativeCreateContext(securityMode, bridgeToken, requireHandshakeRaw) }
            .getOrElse { error ->
                bridgeCoreLogError("bridge-core context creation threw", error)
                return null
            }
    if (handle == 0L) {
        bridgeCoreLogError("bridge-core context creation returned null handle")
        return null
    }
    val requireHandshake = runCatching { nativeRequireHandshake(handle) }.getOrDefault(true)
    bridgeCoreLogInfo("bridge-core abi=$abiVersion loaded")
    return BridgeCoreRuntime(handle = handle, requireHandshake = requireHandshake)
}
