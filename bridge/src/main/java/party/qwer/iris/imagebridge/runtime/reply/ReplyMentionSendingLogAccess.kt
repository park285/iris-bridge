package party.qwer.iris.imagebridge.runtime.reply

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.mergeReplyMentionAttachment
import party.qwer.iris.imagebridge.runtime.core.replyMentionAttachmentOrNull

internal object ReplyMentionSendingLogAccess {
    fun injectMentionAttachment(
        sendingLog: Any,
        sourceAttachmentText: String? = null,
    ): Boolean {
        val currentAttachmentText = ReplyMarkdownSendingLogAccess.readAttachmentText(sendingLog)
        val mentionAttachment = sourceAttachmentText?.let(::mentionAttachmentOrNull) ?: return false
        val mergedAttachment =
            currentAttachmentText
                ?.let { mergeMentionAttachment(it, mentionAttachment) }
                ?: mentionAttachment
        ReplyMarkdownSendingLogAccess.writeAttachmentText(sendingLog, mergedAttachment)
        return true
    }

    fun mentionAttachmentOrNull(attachmentText: String): String? = BridgeCore.replyMentionAttachmentOrNull(attachmentText)

    private fun mergeMentionAttachment(
        targetAttachmentText: String,
        mentionAttachmentText: String,
    ): String? = BridgeCore.mergeReplyMentionAttachment(targetAttachmentText, mentionAttachmentText)
}
