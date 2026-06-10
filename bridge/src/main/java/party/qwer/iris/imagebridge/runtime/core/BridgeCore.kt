package party.qwer.iris.imagebridge.runtime.core

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val TAG = "IrisBridge"
private const val LIBRARY_NAME = "iris_bridge_core"

internal const val BRIDGE_CORE_CLOSED_CODE = "BRIDGE_CORE_CLOSED"
internal const val BRIDGE_CORE_ERROR_CODE = "BRIDGE_CORE_ERROR"

private const val BRIDGE_CORE_CLOSED_ENVELOPE =
    """{"ok":false,"errorCode":"$BRIDGE_CORE_CLOSED_CODE","error":"bridge core context is closed"}"""

private const val BRIDGE_CORE_ERROR_ENVELOPE =
    """{"ok":false,"errorCode":"$BRIDGE_CORE_ERROR_CODE","error":"bridge core dispatch failed"}"""

private fun logInfo(message: String) {
    runCatching { Log.i(TAG, message) }
}

private fun logWarn(
    message: String,
    error: Throwable,
) {
    runCatching { Log.w(TAG, message, error) }
}

private fun logError(message: String) {
    runCatching { Log.e(TAG, message) }
}

private fun logError(
    message: String,
    error: Throwable,
) {
    runCatching { Log.e(TAG, message, error) }
}

object BridgeCore {
    const val EXPECTED_ABI_VERSION: Int = 1

    external fun nativeCreateContext(
        mode: String?,
        token: String,
        requireHandshakeRaw: String?,
    ): Long

    external fun nativeDestroyContext(handle: Long)

    external fun nativeHandshakeOnHello(
        handle: Long,
        frameJson: String,
        nowMs: Long,
    ): String

    external fun nativeHandshakeOnClientProof(
        handle: Long,
        frameJson: String,
    ): String

    external fun nativeValidateRequestToken(
        handle: Long,
        requestJson: String,
    ): String

    external fun nativeVerifyLeases(
        handle: Long,
        roomId: Long,
        requestId: String,
        leasesJson: String,
        factsJson: String,
        nowMs: Long,
    ): String

    external fun nativeDedupeAdmit(
        handle: Long,
        key: String,
        nowMs: Long,
    ): String

    external fun nativeDedupeComplete(
        handle: Long,
        key: String,
        responseJson: String,
        nowMs: Long,
    )

    external fun nativeReplyHookSign(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String,
        createdAtEpochMs: Long,
        mentionsHash: String?,
    ): String?

    external fun nativeReplyHookVerify(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String?,
        createdAtEpochMs: Long,
        mentionsHash: String?,
        signature: String?,
        nowEpochMs: Long,
    ): Boolean

    external fun nativeMentionsHashFromJson(mentionsJson: String?): String?

    external fun nativeMentionsHashFromAttachment(attachmentText: String?): String?

    external fun nativeRequireHandshake(handle: Long): Boolean

    external fun nativeAbiVersion(): Int

    fun replyHookVerify(
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
        if (!loadLibraryOnce()) return false
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
            logError("bridge-core reply-hook verify threw", error)
            false
        }
    }

    fun mentionsHashFromAttachment(attachmentText: String?): String? {
        if (!loadLibraryOnce()) return null
        return runCatching { nativeMentionsHashFromAttachment(attachmentText) }
            .getOrElse { error ->
                logError("bridge-core mentions hash threw", error)
                null
            }
    }

    fun loadOrNull(
        securityMode: String?,
        bridgeToken: String,
        requireHandshakeRaw: String?,
    ): BridgeCoreRuntime? {
        if (!loadLibraryOnce()) return null
        val abiVersion = runCatching { nativeAbiVersion() }.getOrNull()
        if (abiVersion != EXPECTED_ABI_VERSION) {
            logError("bridge-core ABI mismatch: expected $EXPECTED_ABI_VERSION, got $abiVersion")
            return null
        }
        val handle =
            runCatching { nativeCreateContext(securityMode, bridgeToken, requireHandshakeRaw) }
                .getOrElse { error ->
                    logError("bridge-core context creation threw", error)
                    return null
                }
        if (handle == 0L) {
            logError("bridge-core context creation returned null handle")
            return null
        }
        val requireHandshake = runCatching { nativeRequireHandshake(handle) }.getOrDefault(true)
        logInfo("bridge-core abi=$abiVersion loaded")
        return BridgeCoreRuntime(handle = handle, requireHandshake = requireHandshake)
    }

    private fun loadLibraryOnce(): Boolean =
        runCatching { System.loadLibrary(LIBRARY_NAME) }
            .onFailure { logError("bridge-core load failed", it) }
            .isSuccess
}

class BridgeCoreRuntime internal constructor(
    val handle: Long,
    val requireHandshake: Boolean,
) : AutoCloseable {
    private val destroyed = AtomicBoolean(false)
    private val lifecycle = ReentrantReadWriteLock()

    fun validateRequestToken(requestJson: String): BridgeCoreEnvelope = dispatch { BridgeCore.nativeValidateRequestToken(handle, requestJson) }

    fun handshakeOnHello(
        frameJson: String,
        nowMs: Long,
    ): BridgeCoreEnvelope = dispatch { BridgeCore.nativeHandshakeOnHello(handle, frameJson, nowMs) }

    fun handshakeOnClientProof(frameJson: String): BridgeCoreEnvelope = dispatch { BridgeCore.nativeHandshakeOnClientProof(handle, frameJson) }

    fun verifyLeases(
        roomId: Long,
        requestId: String,
        leasesJson: String,
        factsJson: String,
        nowMs: Long,
    ): BridgeCoreEnvelope = dispatch { BridgeCore.nativeVerifyLeases(handle, roomId, requestId, leasesJson, factsJson, nowMs) }

    fun dedupeAdmit(
        key: String,
        nowMs: Long,
    ): BridgeCoreEnvelope = dispatch { BridgeCore.nativeDedupeAdmit(handle, key, nowMs) }

    fun dedupeComplete(
        key: String,
        responseJson: String,
        nowMs: Long,
    ) {
        lifecycle.read {
            if (destroyed.get()) return
            runCatching { BridgeCore.nativeDedupeComplete(handle, key, responseJson, nowMs) }
                .onFailure { logWarn("bridge-core dedupe complete threw", it) }
        }
    }

    private inline fun dispatch(native: () -> String): BridgeCoreEnvelope =
        lifecycle.read {
            if (destroyed.get()) {
                BridgeCoreEnvelope.parse(BRIDGE_CORE_CLOSED_ENVELOPE)
            } else {
                runCatching { native() }
                    .map(BridgeCoreEnvelope::parse)
                    .getOrElse { error ->
                        logError("bridge-core dispatch threw", error)
                        BridgeCoreEnvelope.parse(BRIDGE_CORE_ERROR_ENVELOPE)
                    }
            }
        }

    override fun close() {
        lifecycle.write {
            if (destroyed.compareAndSet(false, true)) {
                runCatching { BridgeCore.nativeDestroyContext(handle) }
                    .onFailure { logWarn("bridge-core context destroy threw", it) }
            }
        }
    }
}
