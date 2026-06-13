package party.qwer.iris.imagebridge.runtime.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val BRIDGE_CORE_MUX_SESSION_CLOSED_ENVELOPE =
    """{"ok":false,"errorCode":"$BRIDGE_CORE_CLOSED_CODE","error":"bridge core mux session is closed"}"""

private const val BRIDGE_CORE_MUX_SESSION_ERROR_ENVELOPE =
    """{"ok":false,"errorCode":"$BRIDGE_CORE_ERROR_CODE","error":"bridge core mux session dispatch failed"}"""

internal sealed interface BridgeCoreMuxCommand {
    data class Dispatch(
        val correlationId: String,
    ) : BridgeCoreMuxCommand

    data class WritePong(
        val correlationId: String?,
    ) : BridgeCoreMuxCommand

    data class WriteBadRequest(
        val correlationId: String,
        val message: String,
    ) : BridgeCoreMuxCommand

    data class WriteBusy(
        val correlationId: String,
    ) : BridgeCoreMuxCommand

    data class MarkCancelled(
        val correlationId: String,
    ) : BridgeCoreMuxCommand

    data object Close : BridgeCoreMuxCommand

    data object Ignore : BridgeCoreMuxCommand
}

internal fun BridgeCoreEnvelope.muxCommand(): BridgeCoreMuxCommand? {
    if (!isOk) return null
    return when (string("command")) {
        "dispatch" -> BridgeCoreMuxCommand.Dispatch(string("correlationId") ?: return null)
        "writePong" -> BridgeCoreMuxCommand.WritePong(string("correlationId"))
        "writeBadRequest" ->
            BridgeCoreMuxCommand.WriteBadRequest(
                correlationId = string("correlationId") ?: return null,
                message = string("message") ?: return null,
            )
        "writeBusy" -> BridgeCoreMuxCommand.WriteBusy(string("correlationId") ?: return null)
        "markCancelled" -> BridgeCoreMuxCommand.MarkCancelled(string("correlationId") ?: return null)
        "close" -> BridgeCoreMuxCommand.Close
        "ignore" -> BridgeCoreMuxCommand.Ignore
        else -> null
    }
}

internal class BridgeCoreMuxSession internal constructor(
    private val handle: Long,
) : AutoCloseable {
    private val destroyed = AtomicBoolean(false)
    private val lifecycle = ReentrantReadWriteLock()

    fun onFrame(frameJson: String): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniMuxSession.nativeMuxSessionOnFrame(handle, frameJson)
        }

    fun onExecutorRejected(correlationId: String): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniMuxSession.nativeMuxSessionOnExecutorRejected(handle, correlationId)
        }

    fun onRequestCompleted(correlationId: String): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniMuxSession.nativeMuxSessionOnRequestCompleted(handle, correlationId)
        }

    fun isCancelled(correlationId: String): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniMuxSession.nativeMuxSessionIsCancelled(handle, correlationId)
        }

    private inline fun dispatch(native: () -> String): BridgeCoreEnvelope =
        lifecycle.read {
            if (destroyed.get()) {
                BridgeCoreEnvelope.parse(BRIDGE_CORE_MUX_SESSION_CLOSED_ENVELOPE)
            } else {
                runCatching { native() }
                    .map(BridgeCoreEnvelope::parse)
                    .getOrElse { error ->
                        bridgeCoreLogError("bridge-core mux session dispatch threw", error)
                        BridgeCoreEnvelope.parse(BRIDGE_CORE_MUX_SESSION_ERROR_ENVELOPE)
                    }
            }
        }

    override fun close() {
        lifecycle.write {
            if (destroyed.compareAndSet(false, true)) {
                runCatching { BridgeCoreJniMuxSession.nativeDestroyMuxSession(handle) }
                    .onFailure { bridgeCoreLogWarn("bridge-core mux session destroy threw", it) }
            }
        }
    }
}
