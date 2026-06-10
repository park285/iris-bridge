package party.qwer.iris.imagebridge.runtime.core

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "IrisBridge"
private const val LIBRARY_NAME = "iris_bridge_core"

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

    fun loadOrNull(
        securityMode: String?,
        bridgeToken: String,
        requireHandshakeRaw: String?,
    ): BridgeCoreRuntime? {
        if (!loadLibraryOnce()) return null
        val abiVersion = runCatching { nativeAbiVersion() }.getOrNull()
        if (abiVersion != EXPECTED_ABI_VERSION) {
            Log.e(TAG, "bridge-core ABI mismatch: expected $EXPECTED_ABI_VERSION, got $abiVersion")
            return null
        }
        val handle =
            runCatching { nativeCreateContext(securityMode, bridgeToken, requireHandshakeRaw) }
                .getOrElse { error ->
                    Log.e(TAG, "bridge-core context creation threw", error)
                    return null
                }
        if (handle == 0L) {
            Log.e(TAG, "bridge-core context creation returned null handle")
            return null
        }
        val requireHandshake = runCatching { nativeRequireHandshake(handle) }.getOrDefault(true)
        Log.i(TAG, "bridge-core abi=$abiVersion loaded")
        return BridgeCoreRuntime(handle = handle, requireHandshake = requireHandshake)
    }

    private fun loadLibraryOnce(): Boolean =
        runCatching { System.loadLibrary(LIBRARY_NAME) }
            .onFailure { Log.e(TAG, "bridge-core load failed", it) }
            .isSuccess
}

class BridgeCoreRuntime internal constructor(
    val handle: Long,
    val requireHandshake: Boolean,
) : AutoCloseable {
    private val destroyed = AtomicBoolean(false)

    override fun close() {
        if (destroyed.compareAndSet(false, true)) {
            runCatching { BridgeCore.nativeDestroyContext(handle) }
                .onFailure { Log.w(TAG, "bridge-core context destroy threw", it) }
        }
    }
}
