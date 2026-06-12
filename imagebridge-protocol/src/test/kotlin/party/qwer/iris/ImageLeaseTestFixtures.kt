package party.qwer.iris

internal object ImageLeaseTestFixtures {
    @Suppress("DEPRECATION_ERROR")
    fun canonicalJson(payload: ImageLeasePayload): String = ImageLease.canonicalJson(payload)

    @Suppress("DEPRECATION_ERROR")
    fun sign(
        secret: String,
        payload: ImageLeasePayload,
    ): String = ImageLease.sign(secret, payload)

    @Suppress("DEPRECATION_ERROR")
    fun issue(
        secret: String,
        payload: ImageLeasePayload,
    ): SignedImageLease = ImageLease.issue(secret, payload)
}
