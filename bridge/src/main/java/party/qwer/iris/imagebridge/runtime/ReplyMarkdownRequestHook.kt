package party.qwer.iris.imagebridge.runtime

import android.util.Log
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_REQUEST
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionSendingLogAccess

internal fun handleReplyMarkdownRequestArgs(
    args: Array<Any?>,
    tag: String,
    markdownPendingContexts: ReplyMarkdownPendingContextStore,
    mentionPendingContexts: ReplyMentionPendingContextStore,
) {
    val sendingLog = args.getOrNull(1) ?: return
    val roomId = ReplyMarkdownSendingLogAccess.readRoomId(sendingLog) ?: return
    val messageText = ReplyMarkdownSendingLogAccess.readMessageText(sendingLog) ?: return
    val currentSessionId = ReplyMarkdownSendingLogAccess.readAttachmentSessionId(sendingLog)
    val mentionContext = mentionPendingContexts.match(roomId, messageText, currentSessionId)
    val mentionInjected = injectMentionAttachment(tag, sendingLog, roomId, mentionContext?.attachmentText)
    val sessionId = ReplyMarkdownSendingLogAccess.readAttachmentSessionId(sendingLog)
    val context =
        markdownPendingContexts.match(roomId, messageText, sessionId)
            ?: run {
                if (mentionInjected) BridgeDiscovery.recordHook(HOOK_REPLY_MARKDOWN_REQUEST, "room=$roomId mention=true")
                return
            }
    runCatching {
        ReplyMarkdownSendingLogAccess.writeThreadMetadata(sendingLog, context.threadId, context.threadScope)
        BridgeDiscovery.recordHook(
            HOOK_REPLY_MARKDOWN_REQUEST,
            "room=${context.roomId} threadId=${context.threadId} scope=${context.threadScope} mention=$mentionInjected",
        )
    }.onFailure { error ->
        Log.e(tag, "reply-markdown request injection failed room=${context.roomId}", error)
    }
}

private fun injectMentionAttachment(
    tag: String,
    sendingLog: Any,
    roomId: Long,
    attachmentText: String?,
): Boolean =
    runCatching {
        ReplyMentionSendingLogAccess.injectMentionAttachment(sendingLog, attachmentText)
    }.onFailure { error ->
        Log.e(tag, "reply mention attachment injection failed room=$roomId", error)
    }.getOrDefault(false)
