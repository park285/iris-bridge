package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
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

    fun validateRequestToken(requestJson: String): BridgeCoreEnvelope = dispatch { BridgeCoreJniRequest.nativeValidateRequestToken(handle, requestJson) }

    fun validateRequestAdmission(
        action: String,
        requestId: String?,
    ): BridgeCoreEnvelope = dispatch { BridgeCoreJniRequest.nativeValidateRequestAdmission(handle, action, requestId) }

    fun validateTextRequest(
        roomId: Long?,
        message: String?,
        markdown: Boolean,
        attachmentJson: String?,
        mentionsJson: String?,
    ): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniRequest.nativeValidateTextRequest(
                handle,
                roomId != null,
                roomId ?: 0L,
                message,
                markdown,
                attachmentJson,
                mentionsJson,
            )
        }

    fun validateImagePaths(
        imagePaths: List<String>,
        maxPathCount: Int,
        maxPathLength: Int,
    ): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniRequest.nativeValidateImagePaths(
                handle,
                imagePathsJson(imagePaths),
                maxPathCount,
                maxPathLength,
            )
        }

    fun handshakeOnHello(
        frameJson: String,
        nowMs: Long,
        socketName: String,
    ): BridgeCoreEnvelope = dispatch { BridgeCoreJniContext.nativeHandshakeOnHello(handle, frameJson, nowMs, socketName) }

    fun handshakeOnClientProof(frameJson: String): BridgeCoreEnvelope = dispatch { BridgeCoreJniContext.nativeHandshakeOnClientProof(handle, frameJson) }

    fun verifyLeases(
        roomId: Long,
        requestId: String,
        leasesJson: String,
        factsJson: String,
        nowMs: Long,
    ): BridgeCoreEnvelope = dispatch { BridgeCoreJniLease.nativeVerifyLeases(handle, roomId, requestId, leasesJson, factsJson, nowMs) }

    fun imageLeaseFactsJson(canonicalPaths: List<String>): BridgeCoreEnvelope =
        dispatch {
            BridgeCoreJniLease.nativeBuildImageLeaseFacts(imagePathsJson(canonicalPaths))
        }

    fun dedupeAdmit(
        key: String,
        nowMs: Long,
    ): BridgeCoreEnvelope = dispatch { BridgeCoreJniLease.nativeDedupeAdmit(handle, key, nowMs) }

    fun dedupeComplete(
        key: String,
        responseJson: String,
        nowMs: Long,
    ) {
        lifecycle.read {
            if (destroyed.get()) return
            runCatching { BridgeCoreJniLease.nativeDedupeComplete(handle, key, responseJson, nowMs) }
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
                runCatching { BridgeCoreJniContext.nativeDestroyContext(handle) }
                    .onFailure { bridgeCoreLogWarn("bridge-core context destroy threw", it) }
            }
        }
    }

    private fun imagePathsJson(imagePaths: List<String>): String =
        JSONArray()
            .apply {
                imagePaths.forEach(::put)
            }.toString()
}
