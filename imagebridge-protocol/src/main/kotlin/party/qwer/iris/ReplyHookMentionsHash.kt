package party.qwer.iris

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object ReplyHookMentionsHash {
    private const val HASH_SHA256 = "SHA-256"

    fun fromMentionsJson(mentionsJson: String?): String? =
        mentionsJson
            ?.takeUnless { it.isBlank() }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?.let(::fromObject)

    fun fromAttachment(attachmentText: String?): String? =
        attachmentText
            ?.takeUnless { it.isBlank() }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?.let(::fromObject)

    private fun fromObject(source: JSONObject): String? {
        val mentions = source.optJSONArray("mentions")?.takeIf { it.length() > 0 } ?: return null
        val canonical = canonicalJson(mentions)
        return MessageDigest.getInstance(HASH_SHA256).digest(canonical.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun canonicalJson(value: Any?): String =
        when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> {
                val keys =
                    value
                        .keys()
                        .asSequence()
                        .toList()
                        .sorted()
                keys.joinToString(prefix = "{", postfix = "}") { key ->
                    JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
                }
            }
            is JSONArray -> {
                (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index ->
                    canonicalJson(value.get(index))
                }
            }
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
