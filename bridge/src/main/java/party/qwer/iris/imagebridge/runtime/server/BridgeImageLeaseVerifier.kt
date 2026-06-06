package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageLease
import party.qwer.iris.ImageLeaseVerification
import party.qwer.iris.SignedImageLease
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class BridgeImageLeaseVerifier(
    private val expectedToken: String = party.qwer.iris.resolveBridgeToken(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    private data class VerifiedLease(
        val key: String,
        val expiresAtEpochMs: Long,
    )

    private data class LeasePathFacts(
        val sha256Hex: String,
        val byteLength: Long,
        val lastModifiedEpochMs: Long,
    )

    private val consumedLeases = ConcurrentHashMap<String, Long>()

    fun verify(
        requestRoomId: Long,
        requestId: String,
        leases: List<SignedImageLease>,
        validatedPaths: List<ValidatedBridgeImagePath>,
    ) {
        if (leases.isEmpty()) {
            error("image lease required")
        }
        require(requestId.isNotBlank()) { "requestId missing" }
        require(expectedToken.isNotBlank()) { "bridge token must be configured to verify image leases" }
        val now = nowEpochMs()
        pruneConsumed(now)
        val factsByPath = validatedPaths.associate { path -> path.canonicalPath to factsFor(path) }
        val leasedPaths = HashSet<String>()
        val verifiedLeases = ArrayList<VerifiedLease>()
        leases.forEach { lease ->
            when (val outcome = ImageLease.verify(expectedToken, lease, now)) {
                ImageLeaseVerification.VALID -> {
                    val facts = factsByPath[lease.payload.canonicalPath] ?: return@forEach
                    verifyLeasePayload(requestRoomId, requestId, lease, facts)
                    leasedPaths += lease.payload.canonicalPath
                    verifiedLeases +=
                        VerifiedLease(
                            key = consumedLeaseKey(lease.payload.requestId, lease.payload.nonce),
                            expiresAtEpochMs = lease.payload.expiresAtEpochMs,
                        )
                }
                else -> error("image lease verification failed: ${outcome.name}")
            }
        }
        validatedPaths.forEach { path ->
            require(path.canonicalPath in leasedPaths) {
                "image path not covered by a signed lease: ${path.canonicalPath}"
            }
        }
        consumeVerifiedLeases(verifiedLeases)
    }

    private fun verifyLeasePayload(
        requestRoomId: Long,
        requestId: String,
        lease: SignedImageLease,
        facts: LeasePathFacts,
    ) {
        val payload = lease.payload
        require(payload.roomId == requestRoomId) {
            "image lease room mismatch: ${payload.roomId} != $requestRoomId"
        }
        require(payload.requestId == requestId) {
            "image lease requestId mismatch: ${payload.requestId} != $requestId"
        }
        require(payload.byteLength == facts.byteLength) {
            "image lease byte length mismatch: ${payload.canonicalPath}"
        }
        require(payload.lastModifiedEpochMs == facts.lastModifiedEpochMs) {
            "image lease last modified mismatch: ${payload.canonicalPath} expected=${payload.lastModifiedEpochMs} actual=${facts.lastModifiedEpochMs}"
        }
        require(payload.sha256Hex.equals(facts.sha256Hex, ignoreCase = true)) {
            "image lease digest mismatch: ${payload.canonicalPath}"
        }
        require(payload.nonce.isNotBlank()) { "image lease nonce missing" }
    }

    private fun consumeVerifiedLeases(verifiedLeases: List<VerifiedLease>) {
        val uniqueLeases = LinkedHashMap<String, VerifiedLease>()
        verifiedLeases.forEach { lease ->
            require(uniqueLeases.putIfAbsent(lease.key, lease) == null) {
                "image lease replay detected"
            }
        }
        val inserted = ArrayList<VerifiedLease>()
        try {
            uniqueLeases.values.forEach { lease ->
                val existing = consumedLeases.putIfAbsent(lease.key, lease.expiresAtEpochMs)
                require(existing == null) { "image lease replay detected" }
                inserted += lease
            }
        } catch (error: RuntimeException) {
            inserted.forEach { lease -> consumedLeases.remove(lease.key, lease.expiresAtEpochMs) }
            throw error
        }
    }

    private fun pruneConsumed(now: Long) {
        consumedLeases.entries.removeIf { (_, expiresAtEpochMs) -> expiresAtEpochMs < now }
    }

    private fun factsFor(path: ValidatedBridgeImagePath): LeasePathFacts {
        val file = File(path.canonicalPath)
        require(file.isFile) { "image file not found: ${path.canonicalPath}" }
        return LeasePathFacts(
            sha256Hex = sha256Hex(file),
            byteLength = file.length(),
            lastModifiedEpochMs = file.lastModified(),
        )
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

    private fun consumedLeaseKey(
        requestId: String,
        nonce: String,
    ): String = "$requestId\n$nonce"

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
