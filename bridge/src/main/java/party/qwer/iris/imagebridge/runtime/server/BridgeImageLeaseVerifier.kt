package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONArray
import org.json.JSONObject
import party.qwer.iris.ImageLeasePayload
import party.qwer.iris.SignedImageLease
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import java.io.File
import java.security.MessageDigest

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
        val factsJson = factsJsonFor(validatedPaths)
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

    private fun factsJsonFor(validatedPaths: List<ValidatedBridgeImagePath>): String {
        val facts = JSONArray()
        validatedPaths.forEach { path ->
            val file = File(path.canonicalPath)
            require(file.isFile) { "image file not found: ${path.canonicalPath}" }
            facts.put(
                JSONObject()
                    .put("canonical_path", path.canonicalPath)
                    .put("sha256_hex", sha256Hex(file))
                    .put("byte_length", file.length())
                    .put("last_modified_epoch_ms", file.lastModified()),
            )
        }
        return facts.toString()
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun leaseRejection(message: String): RuntimeException =
        if (message == "image lease required" || message.startsWith("image lease verification failed:")) {
            IllegalStateException(message)
        } else {
            IllegalArgumentException(message)
        }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
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
