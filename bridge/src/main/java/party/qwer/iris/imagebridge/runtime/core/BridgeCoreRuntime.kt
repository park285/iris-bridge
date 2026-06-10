package party.qwer.iris.imagebridge.runtime.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal const val BRIDGE_CORE_CLOSED_CODE = "BRIDGE_CORE_CLOSED"
internal const val BRIDGE_CORE_ERROR_CODE = "BRIDGE_CORE_ERROR"

private const val BRIDGE_CORE_CLOSED_ENVELOPE =
    """{"ok":false,"errorCode":"$BRIDGE_CORE_CLOSED_CODE","error":"bridge core context is closed"}"""

private const val BRIDGE_CORE_ERROR_ENVELOPE =
    """{"ok":false,"errorCode":"$BRIDGE_CORE_ERROR_CODE","error":"bridge core dispatch failed"}"""

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
        socketName: String,
    ): BridgeCoreEnvelope = dispatch { BridgeCore.nativeHandshakeOnHello(handle, frameJson, nowMs, socketName) }

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
                .onFailure { bridgeCoreLogWarn("bridge-core dedupe complete threw", it) }
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
                        bridgeCoreLogError("bridge-core dispatch threw", error)
                        BridgeCoreEnvelope.parse(BRIDGE_CORE_ERROR_ENVELOPE)
                    }
            }
        }

    override fun close() {
        lifecycle.write {
            if (destroyed.compareAndSet(false, true)) {
                runCatching { BridgeCore.nativeDestroyContext(handle) }
                    .onFailure { bridgeCoreLogWarn("bridge-core context destroy threw", it) }
            }
        }
    }
}
