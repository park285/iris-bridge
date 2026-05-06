package party.qwer.iris.imagebridge.runtime.reply

import org.json.JSONObject

internal object ReplyMentionSendingLogAccess {
    fun injectMentionAttachment(sendingLog: Any): Boolean {
        val attachmentText = ReplyMarkdownSendingLogAccess.readAttachmentText(sendingLog) ?: return false
        val mentionAttachment = mentionAttachmentOrNull(attachmentText) ?: return false
        ReplyMarkdownSendingLogAccess.writeAttachmentText(sendingLog, mentionAttachment)
        return true
    }

    private fun mentionAttachmentOrNull(attachmentText: String): String? =
        runCatching {
            val attachment = JSONObject(attachmentText)
            val mentions = attachment.optJSONArray("mentions") ?: return null
            if (mentions.length() == 0) return null
            attachment.toString()
        }.getOrNull()
}
