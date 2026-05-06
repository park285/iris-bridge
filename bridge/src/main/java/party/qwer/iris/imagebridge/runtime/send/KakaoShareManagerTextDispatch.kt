package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionSendingLogAccess

internal fun canUseShareManagerTextPath(
    markdown: Boolean,
    threadId: Long?,
    threadScope: Int?,
    shareManagerTextInvoker: KakaoShareManagerTextInvoker?,
): Boolean =
    !markdown &&
        threadId == null &&
        threadScope == null &&
        shareManagerTextInvoker != null

internal fun sendWithShareManagerTextPath(
    chatRoom: Any,
    roomId: Long,
    message: String,
    replyAttachment: String?,
    requestId: String?,
    shareManagerTextInvoker: KakaoShareManagerTextInvoker?,
    mentionPendingContexts: ReplyMentionPendingContextStore?,
    logInfo: (String, String) -> Unit,
): Boolean {
    if (shareManagerTextInvoker == null) return false
    val mentionAttachment = replyAttachment?.let(ReplyMentionSendingLogAccess::mentionAttachmentOrNull)
    return when {
        mentionAttachment != null && mentionPendingContexts != null -> {
            mentionPendingContexts.remember(
                ReplyMentionPendingContext(
                    roomId = roomId,
                    messageText = message,
                    attachmentText = mentionAttachment,
                    sessionId = requestId,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
            logInfo(
                KAKAO_TEXT_SEND_TAG,
                "text send mention pending context remembered requestId=$requestId room=$roomId",
            )
            shareManagerTextInvoker.invoke(chatRoom, message)
            true
        }
        replyAttachment == null -> {
            shareManagerTextInvoker.invoke(chatRoom, message)
            true
        }
        else -> false
    }
}
