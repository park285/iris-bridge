package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONArray
import org.json.JSONObject
import party.qwer.iris.ImageLeasePayload
import party.qwer.iris.SignedImageLease
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.imageLeaseRejectionIsStateError
import party.qwer.iris.imagebridge.runtime.core.loadOrNull

internal class BridgeImageLeaseVerifier private constructor(
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
    private val nowEpochMs: () -> Long,
) {
    constructor(
        expectedToken: String = party.qwer.iris.resolveBridgeToken(),
        nowEpochMs: () -> Long = { System.currentTimeMillis() },
    ) : this(bridgeCoreProviderFor(expectedToken), nowEpochMs)

    constructor(
        bridgeCore: BridgeCoreRuntime,
        nowEpochMs: () -> Long = { System.currentTimeMillis() },
    ) : this({ bridgeCore }, nowEpochMs)

    fun verify(
        requestRoomId: Long,
        requestId: String,
        leases: List<SignedImageLease>,
        validatedPaths: List<ValidatedBridgeImagePath>,
    ) {
        val core = bridgeCoreProvider() ?: error("bridge core unavailable to verify image leases")
        val factsJson = factsJsonFor(core, validatedPaths)
        val leasesJson = leasesJsonFor(leases)
        val envelope = core.verifyLeases(requestRoomId, requestId, leasesJson, factsJson, nowEpochMs())
        if (!envelope.isOk) {
            throw leaseRejection(envelope.errorMessage ?: "image lease verification failed")
        }
    }

    private fun leasesJsonFor(leases: List<SignedImageLease>): String {
        val array = JSONArray()
        leases.forEach { lease ->
            array.put(
                JSONObject()
                    .put("payload", payloadJson(lease.payload))
                    .put("signature", lease.signature),
            )
        }
        return array.toString()
    }

    private fun payloadJson(payload: ImageLeasePayload): JSONObject =
        JSONObject()
            .put("version", payload.version)
            .put("requestId", payload.requestId)
            .put("roomId", payload.roomId)
            .put("imageIndex", payload.imageIndex)
            .put("canonicalPath", payload.canonicalPath)
            .put("sha256Hex", payload.sha256Hex)
            .put("byteLength", payload.byteLength)
            .put("contentType", payload.contentType)
            .put("lastModifiedEpochMs", payload.lastModifiedEpochMs)
            .put("expiresAtEpochMs", payload.expiresAtEpochMs)
            .put("nonce", payload.nonce)

    private fun factsJsonFor(
        core: BridgeCoreRuntime,
        validatedPaths: List<ValidatedBridgeImagePath>,
    ): String {
        val envelope = core.imageLeaseFactsJson(validatedPaths.map { path -> path.canonicalPath })
        if (!envelope.isOk) {
            throw leaseRejection(envelope.errorMessage ?: "image lease facts generation failed")
        }
        return envelope.string("factsJson") ?: throw leaseRejection("image lease facts missing")
    }

    private fun leaseRejection(message: String): RuntimeException =
        if (BridgeCore.imageLeaseRejectionIsStateError(message)) {
            IllegalStateException(message)
        } else {
            IllegalArgumentException(message)
        }
}

private fun bridgeCoreProviderFor(expectedToken: String): () -> BridgeCoreRuntime? {
    val runtime by lazy {
        BridgeCore.loadOrNull(
            securityMode = BridgeSecurityMode.fromEnv().coreRawValue(),
            bridgeToken = expectedToken,
            requireHandshakeRaw = null,
        )
    }
    return { runtime }
}
