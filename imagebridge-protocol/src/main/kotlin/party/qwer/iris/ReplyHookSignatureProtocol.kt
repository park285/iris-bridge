package party.qwer.iris

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ReplyHookSignatureProtocol {
    const val EXTRA_SIGNATURE = "party.qwer.iris.extra.SIGNATURE"
    const val TTL_MS = 2 * 60_000L

    private const val DOMAIN = "iris-reply-hook-v1"
    private const val HMAC_SHA256 = "HmacSHA256"

    fun signOrNull(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String,
        createdAtEpochMs: Long,
        mentionsJson: String?,
    ): String? =
        signPreparedOrNull(
            bridgeToken = bridgeToken,
            roomId = roomId,
            messageText = messageText,
            sessionId = sessionId,
            createdAtEpochMs = createdAtEpochMs,
            mentionsHash = mentionsHashFromMentionsJson(mentionsJson),
        )

    fun signPreparedOrNull(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String,
        createdAtEpochMs: Long,
        mentionsHash: String?,
    ): String? {
        val token = bridgeToken.trim()
        val session = sessionId.trim()
        if (token.isEmpty() || session.isEmpty() || messageText.isBlank()) return null
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), HMAC_SHA256))
        listOf(
            DOMAIN,
            roomId.toString(),
            messageText,
            session,
            createdAtEpochMs.toString(),
            mentionsHash.orEmpty(),
        ).forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            mac.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
            mac.update(0)
            mac.update(bytes)
        }
        return mac.doFinal().toHex()
    }

    fun verify(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String?,
        createdAtEpochMs: Long?,
        mentionsHash: String?,
        signature: String?,
        nowEpochMs: Long,
        ttlMs: Long = TTL_MS,
    ): Boolean {
        val createdAt = createdAtEpochMs ?: return false
        if (createdAt > nowEpochMs || nowEpochMs - createdAt > ttlMs) return false
        val expected =
            signPreparedOrNull(
                bridgeToken = bridgeToken,
                roomId = roomId,
                messageText = messageText,
                sessionId = sessionId.orEmpty(),
                createdAtEpochMs = createdAt,
                mentionsHash = mentionsHash,
            ) ?: return false
        return MessageDigest.isEqual(
            signature.orEmpty().toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }

    fun mentionsHashFromMentionsJson(mentionsJson: String?): String? = ReplyHookMentionsHash.fromMentionsJson(mentionsJson)

    fun mentionsHashFromAttachment(attachmentText: String?): String? = ReplyHookMentionsHash.fromAttachment(attachmentText)

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
