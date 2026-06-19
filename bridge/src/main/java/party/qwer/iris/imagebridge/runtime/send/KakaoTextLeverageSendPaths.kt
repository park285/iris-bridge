package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore

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
    kakaoLinkCommitVerifier: KakaoChatLogCommitVerifier?,
    logInfo: (String, String) -> Unit,
): Boolean {
    val resolvedIrisTemplate = hasResolvedIrisKakaoLinkTemplate(rawAttachment)
    if (hasExplicitKakaoLinkTemplateArgs(rawAttachment)) {
        if (
            runKakaoLinkSpecPathOrFalse(
                binding,
                chatRoom,
                roomId,
                message,
                threadId,
                threadScope,
                rawAttachment,
                requestId,
                leverageCommitPendingContexts,
                kakaoLinkSpecSender,
                leverageAttachmentPatcher,
                kakaoLinkCommitVerifier,
                logInfo,
            )
        ) {
            return true
        }
        error("Karing template send did not create chat log")
    }
    if (resolvedIrisTemplate) {
        if (sendWithShareManagerLeveragePath(
                binding,
                chatRoom,
                roomId,
                message,
                threadId,
                threadScope,
                rawAttachment,
                requestId,
                leveragePendingContexts,
                logInfo,
            )
        ) {
            return true
        }
        return sendWithLeverageSchemePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, logInfo)
    }
    if (
        sendWithKakaoLinkSpecPath(
            binding = binding,
            chatRoom = chatRoom,
            roomId = roomId,
            message = message,
            threadId = threadId,
            threadScope = threadScope,
            rawAttachment = rawAttachment,
            requestId = requestId,
            leverageCommitPendingContexts = leverageCommitPendingContexts,
            kakaoLinkSpecSender = kakaoLinkSpecSender,
            leverageAttachmentPatcher = leverageAttachmentPatcher,
            kakaoLinkCommitVerifier = kakaoLinkCommitVerifier,
            logInfo = logInfo,
        )
    ) {
        return true
    }
    if (sendWithShareManagerLeveragePath(
            binding,
            chatRoom,
            roomId,
            message,
            threadId,
            threadScope,
            rawAttachment,
            requestId,
            leveragePendingContexts,
            logInfo,
        )
    ) {
        return true
    }
    return sendWithLeverageSchemePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, logInfo)
}
