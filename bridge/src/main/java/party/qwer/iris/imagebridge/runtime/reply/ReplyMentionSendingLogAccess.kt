package party.qwer.iris.imagebridge.runtime.reply

import org.json.JSONObject

internal object ReplyMentionSendingLogAccess {
    fun injectMentionAttachment(
        sendingLog: Any,
        sourceAttachmentText: String? = null,
    ): Boolean {
        val currentAttachmentText = ReplyMarkdownSendingLogAccess.readAttachmentText(sendingLog) ?: return false
        val mentionAttachment = mentionAttachmentOrNull(sourceAttachmentText ?: currentAttachmentText) ?: return false
        val mergedAttachment = mergeMentionAttachment(currentAttachmentText, mentionAttachment) ?: return false
        ReplyMarkdownSendingLogAccess.writeAttachmentText(sendingLog, mergedAttachment)
        return true
    }

    fun mentionAttachmentOrNull(attachmentText: String): String? =
        runCatching {
            val attachment = JSONObject(attachmentText)
            val mentions = attachment.optJSONArray("mentions") ?: return null
            if (mentions.length() == 0) return null
            attachment.toString()
        }.getOrNull()

    private fun mergeMentionAttachment(
        targetAttachmentText: String,
        mentionAttachmentText: String,
    ): String? =
        runCatching {
            val target = JSONObject(targetAttachmentText)
            val source = JSONObject(mentionAttachmentText)
            val mentions = source.optJSONArray("mentions") ?: return null
            if (mentions.length() == 0) return null
            target.put("mentions", mentions)
            target.toString()
        }.getOrNull()
}
