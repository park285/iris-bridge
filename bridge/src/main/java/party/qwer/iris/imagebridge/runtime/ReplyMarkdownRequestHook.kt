package party.qwer.iris.imagebridge.runtime

import android.util.Log
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.mergeReplyLeverageAttachment
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_REQUEST
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionSendingLogAccess

internal fun handleReplyMarkdownRequestArgs(
    args: Array<Any?>,
    tag: String,
    markdownPendingContexts: ReplyMarkdownPendingContextStore,
    mentionPendingContexts: ReplyMentionPendingContextStore,
    leveragePendingContexts: ReplyLeveragePendingContextStore,
    leverageMessageType: Any?,
    leverageWriteType: Any?,
) {
    val sendingLog = args.getOrNull(1) ?: return
    val roomId = ReplyMarkdownSendingLogAccess.readRoomId(sendingLog) ?: return
    val currentSessionId = ReplyMarkdownSendingLogAccess.readAttachmentSessionId(sendingLog)
    val messageText = ReplyMarkdownSendingLogAccess.readMessageText(sendingLog)
    val leverageContext =
        if (messageText.isNullOrBlank()) {
            leveragePendingContexts.matchLatest(roomId, currentSessionId)
        } else {
            leveragePendingContexts.match(roomId, messageText, currentSessionId)
                ?: leveragePendingContexts.matchLatest(roomId, currentSessionId)
        }
    if (leverageContext != null) {
        injectLeverageAttachment(tag, args, sendingLog, leverageContext, leverageMessageType, leverageWriteType)
        return
    }
    if (messageText == null) return
    val mentionContext = mentionPendingContexts.match(roomId, messageText, currentSessionId)
    val mentionInjected = injectMentionAttachment(tag, sendingLog, roomId, mentionContext?.attachmentText)
    val sessionId = ReplyMarkdownSendingLogAccess.readAttachmentSessionId(sendingLog)
    val context =
        markdownPendingContexts.match(roomId, messageText, sessionId)
            ?: run {
                if (mentionInjected) defaultBridgeDiscovery.recordHook(HOOK_REPLY_MARKDOWN_REQUEST, "room=$roomId mention=true")
                return
            }
    runCatching {
        ReplyMarkdownSendingLogAccess.writeThreadMetadata(sendingLog, context.threadId, context.threadScope)
        defaultBridgeDiscovery.recordHook(
            HOOK_REPLY_MARKDOWN_REQUEST,
            "room=${context.roomId} threadId=${context.threadId} scope=${context.threadScope} mention=$mentionInjected",
        )
    }.onFailure { error ->
        Log.e(tag, "reply-markdown request injection failed room=${context.roomId}", error)
    }
}

private fun injectLeverageAttachment(
    tag: String,
    args: Array<Any?>,
    sendingLog: Any,
    context: ReplyLeveragePendingContext,
    leverageMessageType: Any?,
    leverageWriteType: Any?,
) {
    runCatching {
        requireNotNull(leverageMessageType) { "Leverage message type unavailable" }
        val generatedAttachment = ReplyMarkdownSendingLogAccess.readAttachmentText(sendingLog)
        val attachmentText = mergeLeverageAttachment(generatedAttachment, context.attachmentText)
        ReplyMarkdownSendingLogAccess.writeAttachmentText(sendingLog, attachmentText)
        ReplyMarkdownSendingLogAccess.writeMessageType(sendingLog, leverageMessageType)
        if (leverageWriteType != null) {
            args[2] = leverageWriteType
        }
        if (context.threadId != null && context.threadScope != null) {
            ReplyMarkdownSendingLogAccess.writeThreadMetadata(sendingLog, context.threadId, context.threadScope)
        }
        safeLogInfo(
            tag,
            "reply leverage attachment injected room=${context.roomId} generated=${generatedAttachment != null} " +
                "connectWriteType=${leverageWriteType != null}",
        )
        defaultBridgeDiscovery.recordHook(
            HOOK_REPLY_MARKDOWN_REQUEST,
            "room=${context.roomId} leverage=true threadId=${context.threadId} scope=${context.threadScope}",
        )
    }.onFailure { error ->
        safeLogError(tag, "reply leverage attachment injection failed room=${context.roomId}", error)
    }
}

internal fun mergeLeverageAttachment(
    generatedAttachment: String?,
    rawAttachment: String,
): String =
    mergeLeverageAttachment(
        generatedAttachment,
        rawAttachment,
        BridgeCore::mergeReplyLeverageAttachment,
    )

internal fun mergeLeverageAttachment(
    generatedAttachment: String?,
    rawAttachment: String,
    mergeAttachment: (String?, String) -> String?,
): String =
    mergeAttachment(generatedAttachment, rawAttachment)
        ?: error("bridge core unavailable to merge reply leverage attachment")

private fun injectMentionAttachment(
    tag: String,
    sendingLog: Any,
    roomId: Long,
    attachmentText: String?,
): Boolean =
    runCatching {
        ReplyMentionSendingLogAccess.injectMentionAttachment(sendingLog, attachmentText)
    }.onFailure { error ->
        safeLogError(tag, "reply mention attachment injection failed room=$roomId", error)
    }.getOrDefault(false)

private fun safeLogInfo(
    tag: String,
    message: String,
) {
    runCatching { Log.i(tag, message) }
}

private fun safeLogError(
    tag: String,
    message: String,
    error: Throwable,
) {
    runCatching { Log.e(tag, message, error) }
}
