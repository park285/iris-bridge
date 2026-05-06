package party.qwer.iris.imagebridge.runtime.reply

import org.json.JSONObject

internal object ReplyMentionSendingLogAccess {
    fun injectMentionAttachment(
        sendingLog: Any,
        sourceAttachmentText: String? = null,
    ): Boolean {
        val currentAttachmentText = ReplyMarkdownSendingLogAccess.readAttachmentText(sendingLog)
        val sourceText = sourceAttachmentText ?: currentAttachmentText ?: return false
        val mentionAttachment = mentionAttachmentOrNull(sourceText) ?: return false
        val mergedAttachment =
            currentAttachmentText
                ?.let { mergeMentionAttachment(it, mentionAttachment) }
                ?: mentionAttachment
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
