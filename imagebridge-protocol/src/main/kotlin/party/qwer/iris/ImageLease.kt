package party.qwer.iris

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

enum class ImageLeaseVerification {
    VALID,
    SIGNATURE_MISMATCH,
    EXPIRED,
    ;

    val valid: Boolean get() = this == VALID
}

object ImageLease {
    const val VERSION = 1

    private const val HMAC_SHA256 = "HmacSHA256"
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    fun canonicalJson(payload: ImageLeasePayload): String {
        val out = StringBuilder()
        out.append('{')
        appendNumber(out, "version", payload.version.toString(), first = true)
        appendString(out, "requestId", payload.requestId)
        appendNumber(out, "roomId", payload.roomId.toString())
        appendNumber(out, "imageIndex", payload.imageIndex.toString())
        appendString(out, "canonicalPath", payload.canonicalPath)
        appendString(out, "sha256Hex", payload.sha256Hex)
        appendNumber(out, "byteLength", payload.byteLength.toString())
        appendString(out, "contentType", payload.contentType)
        appendNumber(out, "lastModifiedEpochMs", payload.lastModifiedEpochMs.toString())
        appendNumber(out, "expiresAtEpochMs", payload.expiresAtEpochMs.toString())
        appendString(out, "nonce", payload.nonce)
        out.append('}')
        return out.toString()
    }

    fun sign(
        secret: String,
        payload: ImageLeasePayload,
    ): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256))
        return mac.doFinal(canonicalJson(payload).toByteArray(Charsets.UTF_8)).toHex()
    }

    fun issue(
        secret: String,
        payload: ImageLeasePayload,
    ): SignedImageLease = SignedImageLease(payload, sign(secret, payload))

    fun verify(
        secret: String,
        lease: SignedImageLease,
        nowEpochMs: Long,
    ): ImageLeaseVerification {
        val expected = sign(secret, lease.payload)
        val signatureOk =
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                lease.signature.toByteArray(Charsets.UTF_8),
            )
        if (!signatureOk) return ImageLeaseVerification.SIGNATURE_MISMATCH
        if (nowEpochMs > lease.payload.expiresAtEpochMs) return ImageLeaseVerification.EXPIRED
        return ImageLeaseVerification.VALID
    }

    private fun appendNumber(
        out: StringBuilder,
        key: String,
        value: String,
        first: Boolean = false,
    ) {
        appendKey(out, key, first)
        out.append(value)
    }

    private fun appendString(
        out: StringBuilder,
        key: String,
        value: String,
        first: Boolean = false,
    ) {
        appendKey(out, key, first)
        out.append('"')
        escapeJsonInto(out, value)
        out.append('"')
    }

    private fun appendKey(
        out: StringBuilder,
        key: String,
        first: Boolean,
    ) {
        if (!first) out.append(',')
        out.append('"').append(key).append("\":")
    }

    private fun escapeJsonInto(
        out: StringBuilder,
        value: String,
    ) {
        for (ch in value) {
            when (ch.code) {
                '"'.code -> out.append("\\\"")
                '\\'.code -> out.append("\\\\")
                0x0A -> out.append("\\n")
                0x0D -> out.append("\\r")
                0x09 -> out.append("\\t")
                0x08 -> out.append("\\b")
                0x0C -> out.append("\\f")
                else ->
                    if (ch.code < 0x20) {
                        appendUnicodeEscape(out, ch.code)
                    } else {
                        out.append(ch)
                    }
            }
        }
    }

    private fun appendUnicodeEscape(
        out: StringBuilder,
        code: Int,
    ) {
        out.append("\\u")
        for (shift in intArrayOf(12, 8, 4, 0)) {
            out.append(HEX_DIGITS[(code shr shift) and 0xF])
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
