package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageLease
import party.qwer.iris.ImageLeaseVerification
import party.qwer.iris.SignedImageLease

internal class BridgeImageLeaseVerifier(
    private val expectedToken: String = party.qwer.iris.resolveBridgeToken(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    fun verify(
        leases: List<SignedImageLease>,
        validatedPaths: List<ValidatedBridgeImagePath>,
    ) {
        if (leases.isEmpty()) {
            error("image lease required")
        }
        require(expectedToken.isNotBlank()) { "bridge token must be configured to verify image leases" }
        val now = nowEpochMs()
        val leasedPaths = HashSet<String>()
        leases.forEach { lease ->
            when (val outcome = ImageLease.verify(expectedToken, lease, now)) {
                ImageLeaseVerification.VALID -> leasedPaths += lease.payload.canonicalPath
                else -> error("image lease verification failed: ${outcome.name}")
            }
        }
        validatedPaths.forEach { path ->
            require(path.canonicalPath in leasedPaths) {
                "image path not covered by a signed lease: ${path.canonicalPath}"
            }
        }
    }
}
