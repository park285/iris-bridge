package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageLease
import party.qwer.iris.ImageLeasePayload
import party.qwer.iris.SignedImageLease

internal object BridgeImageLeaseTestFixtures {
    @Suppress("DEPRECATION_ERROR")
    fun issue(
        secret: String,
        payload: ImageLeasePayload,
    ): SignedImageLease = ImageLease.issue(secret, payload)
}
