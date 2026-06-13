package party.qwer.iris

import kotlinx.serialization.Serializable

@Serializable
data class ImageLeasePayload(
    val version: Int,
    val requestId: String,
    val roomId: Long,
    val imageIndex: Int,
    val canonicalPath: String,
    val sha256Hex: String,
    val byteLength: Long,
    val contentType: String,
    val lastModifiedEpochMs: Long,
    val expiresAtEpochMs: Long,
    val nonce: String,
)

@Serializable
data class SignedImageLease(
    val payload: ImageLeasePayload,
    val signature: String,
)

object ImageLease {
    const val VERSION = 1
}
