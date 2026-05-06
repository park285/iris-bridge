package party.qwer.iris

import org.json.JSONObject

object ReplyAttachmentProtocol {
    fun build(
        markdown: Boolean,
        mentionsJson: String?,
        sessionId: String? = null,
    ): String? {
        if (!markdown && mentionsJson.isNullOrBlank() && sessionId.isNullOrBlank()) return null
        return JSONObject()
            .apply {
                put("callingPkg", "com.kakao.talk")
                if (markdown) {
                    put("markdown", true)
                    put("f", true)
                }
                copyMentionsInto(this, mentionsJson)
                if (!sessionId.isNullOrBlank()) {
                    put("irisSessionId", sessionId)
                }
            }.toString()
    }

    private fun copyMentionsInto(
        target: JSONObject,
        mentionsJson: String?,
    ) {
        if (mentionsJson.isNullOrBlank()) return
        val source = runCatching { JSONObject(mentionsJson) }.getOrNull() ?: return
        val mentions = source.optJSONArray("mentions") ?: return
        if (mentions.length() > 0) {
            target.put("mentions", mentions)
        }
    }
}
