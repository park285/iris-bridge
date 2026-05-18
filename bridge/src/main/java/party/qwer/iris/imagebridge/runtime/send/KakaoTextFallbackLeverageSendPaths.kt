package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess

internal fun sendWithShareManagerLeveragePath(
    binding: KakaoTextSendBinding,
    chatRoom: Any,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    leveragePendingContexts: ReplyLeveragePendingContextStore?,
    logInfo: (String, String) -> Unit,
): Boolean {
    val shareManagerTextInvoker = binding.shareManagerTextInvoker ?: return false
    rememberLeverageContext(roomId, message, threadId, threadScope, rawAttachment, requestId, leveragePendingContexts, logInfo, "pending")
    shareManagerTextInvoker.invoke(chatRoom, message)
    return true
}

internal fun sendWithLeverageSchemePath(
    binding: KakaoTextSendBinding,
    chatRoom: Any,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    logInfo: (String, String) -> Unit,
): Boolean =
    runCatching {
        val writeType = binding.leverageSchemeWriteType ?: return false
        val sendingLog =
            binding.leverageSendingLogFactory.newSendingLog(
                roomId = roomId,
                chatRoom = chatRoom,
                message = message,
                markdown = false,
                mentionsJson = null,
                requestId = requestId,
                replyAttachment = rawAttachment,
            )
        if (threadId != null && threadScope != null) {
            ReplyMarkdownSendingLogAccess.writeThreadMetadata(sendingLog, threadId, threadScope)
        }
        binding.invoke(chatRoom, sendingLog, writeType)
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "text send leverage scheme invoked requestId=$requestId room=$roomId " +
                "threadId=$threadId messageLength=${message.length}",
        )
        true
    }.onFailure { error ->
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "text send leverage scheme failed requestId=$requestId room=$roomId " +
                "error=${error.javaClass.name}: ${error.message}",
        )
    }.getOrDefault(false)

internal fun rememberLeverageContext(
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    contexts: ReplyLeveragePendingContextStore?,
    logInfo: (String, String) -> Unit,
    kind: String,
) {
    contexts?.remember(
        ReplyLeveragePendingContext(
            roomId = roomId,
            messageText = message,
            attachmentText = rawAttachment,
            threadId = threadId,
            threadScope = threadScope,
            sessionId = requestId,
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )
    logInfo(KAKAO_TEXT_SEND_TAG, "text send leverage $kind context remembered requestId=$requestId room=$roomId")
}
