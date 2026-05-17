package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess

internal fun trySendWithLeveragePaths(
    binding: KakaoTextSendBinding,
    chatRoom: Any,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    leveragePendingContexts: ReplyLeveragePendingContextStore?,
    leverageCommitPendingContexts: ReplyLeveragePendingContextStore?,
    kakaoLinkSpecSender: KakaoLinkSpecSender?,
    leverageAttachmentPatcher: KakaoLeverageAttachmentPatcher?,
    logInfo: (String, String) -> Unit,
): Boolean {
    if (
        sendWithKakaoLinkSpecPath(
            binding = binding,
            roomId = roomId,
            message = message,
            threadId = threadId,
            threadScope = threadScope,
            rawAttachment = rawAttachment,
            requestId = requestId,
            leverageCommitPendingContexts = leverageCommitPendingContexts,
            kakaoLinkSpecSender = kakaoLinkSpecSender,
            leverageAttachmentPatcher = leverageAttachmentPatcher,
            logInfo = logInfo,
        )
    ) {
        return true
    }
    if (sendWithShareManagerLeveragePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, leveragePendingContexts, logInfo)) {
        return true
    }
    return sendWithLeverageSchemePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, logInfo)
}

private fun sendWithKakaoLinkSpecPath(
    binding: KakaoTextSendBinding,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    leverageCommitPendingContexts: ReplyLeveragePendingContextStore?,
    kakaoLinkSpecSender: KakaoLinkSpecSender?,
    leverageAttachmentPatcher: KakaoLeverageAttachmentPatcher?,
    logInfo: (String, String) -> Unit,
): Boolean {
    if (threadId != null || threadScope != null) {
        return false
    }
    val sender = kakaoLinkSpecSender ?: binding.kakaoLinkSpecSender ?: return false
    val serverGeneratedTemplate = hasExplicitKakaoLinkTemplateArgs(rawAttachment)
    if (!serverGeneratedTemplate) {
        rememberLeverageContext(roomId, message, threadId, threadScope, rawAttachment, requestId, leverageCommitPendingContexts, logInfo, "commit")
    }
    if (!sender.send(roomId, message, rawAttachment, requestId)) return false
    if (!serverGeneratedTemplate) {
        val patcher = leverageAttachmentPatcher ?: binding.leverageAttachmentPatcher
        patcher?.patchAsync(roomId, message, rawAttachment, requestId)
    }
    return true
}

private fun sendWithShareManagerLeveragePath(
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

private fun sendWithLeverageSchemePath(
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

private fun rememberLeverageContext(
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
