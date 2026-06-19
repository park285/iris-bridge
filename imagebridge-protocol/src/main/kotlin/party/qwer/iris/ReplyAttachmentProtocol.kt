package party.qwer.iris

import org.json.JSONObject

object ReplyAttachmentProtocol {
    fun build(
        markdown: Boolean,
        mentionsJson: String?,
        sessionId: String? = null,
    ): String? {
        val mentions = parseMentions(mentionsJson)
        if (!markdown && mentions == null) return null
        return JSONObject()
            .apply {
                if (markdown) {
                    put("callingPkg", "com.kakao.talk")
                    put("markdown", true)
                    put("f", true)
                }
                if (mentions != null) {
                    put("mentions", mentions)
                }
            }.toString()
    }

    private fun parseMentions(mentionsJson: String?) =
        mentionsJson
            ?.takeUnless { it.isBlank() }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?.optJSONArray("mentions")
            ?.takeIf { mentions -> mentions.length() > 0 }
}
