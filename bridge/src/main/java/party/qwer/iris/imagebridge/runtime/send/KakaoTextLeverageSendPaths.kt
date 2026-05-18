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
        if (sendWithShareManagerLeveragePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, leveragePendingContexts, logInfo)) {
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
    if (sendWithShareManagerLeveragePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, leveragePendingContexts, logInfo)) {
        return true
    }
    return sendWithLeverageSchemePath(binding, chatRoom, roomId, message, threadId, threadScope, rawAttachment, requestId, logInfo)
}

private fun runKakaoLinkSpecPathOrFalse(
    binding: KakaoTextSendBinding,
    chatRoom: Any,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    leverageCommitPendingContexts: ReplyLeveragePendingContextStore?,
    kakaoLinkSpecSender: KakaoLinkSpecSender?,
    leverageAttachmentPatcher: KakaoLeverageAttachmentPatcher?,
    kakaoLinkCommitVerifier: KakaoChatLogCommitVerifier?,
    logInfo: (String, String) -> Unit,
): Boolean =
    runCatching {
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
    }.onFailure { error ->
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "text send kakaolink spec path unavailable requestId=$requestId room=$roomId " +
                "error=${error.javaClass.name}: ${error.message}",
        )
    }.getOrDefault(false)

private fun sendWithKakaoLinkSpecPath(
    binding: KakaoTextSendBinding,
    chatRoom: Any,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    rawAttachment: String,
    requestId: String?,
    leverageCommitPendingContexts: ReplyLeveragePendingContextStore?,
    kakaoLinkSpecSender: KakaoLinkSpecSender?,
    leverageAttachmentPatcher: KakaoLeverageAttachmentPatcher?,
    kakaoLinkCommitVerifier: KakaoChatLogCommitVerifier?,
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
    val verifier =
        if (serverGeneratedTemplate) {
            kakaoLinkCommitVerifier ?: binding.kakaoLinkCommitVerifier ?: error("KakaoLinkSpec commit verifier unavailable")
        } else {
            null
        }
    val minimumCreatedAt = System.currentTimeMillis() / 1000L - KAKAO_LINK_COMMIT_CREATED_AT_GRACE_SECONDS
    val minimumRowId = verifier?.latestCommittedRowId(roomId) ?: 0L
    val sendAttachment =
        if (serverGeneratedTemplate) {
            kakaoLinkSpecSendAttachment(rawAttachment)
        } else {
            rawAttachment
        }
    val commitVerificationAttachment =
        if (serverGeneratedTemplate) {
            kakaoLinkSpecCommitVerificationAttachment(rawAttachment)
        } else {
            rawAttachment
        }
    if (serverGeneratedTemplate) {
        var sentAtLeastOnce = false
        repeat(KAKAO_LINK_SPEC_SEND_ATTEMPTS) { attempt ->
            if (!sender.send(roomId, chatRoom, message, sendAttachment, requestId)) {
                logInfo(
                    KAKAO_TEXT_SEND_TAG,
                    "kakaolink spec send returned false requestId=$requestId room=$roomId attempt=${attempt + 1}",
                )
                return@repeat
            }
            sentAtLeastOnce = true
            if (verifier?.awaitCommitted(roomId, message, minimumCreatedAt, minimumRowId, requestId, commitVerificationAttachment) == true) {
                val patcher = leverageAttachmentPatcher ?: binding.leverageAttachmentPatcher
                patcher?.patchAsync(roomId, message, rawAttachment, requestId)
                return true
            }
            if (attempt + 1 < KAKAO_LINK_SPEC_SEND_ATTEMPTS) {
                logInfo(
                    KAKAO_TEXT_SEND_TAG,
                    "kakaolink spec commit missing; retrying requestId=$requestId room=$roomId attempt=${attempt + 1}",
                )
            }
        }
        if (!sentAtLeastOnce) {
            error("KakaoLinkSpec server template send failed before chat log commit")
        }
        error("KakaoLinkSpec server template send did not create chat log")
    }
    if (!sender.send(roomId, chatRoom, message, sendAttachment, requestId)) {
        return false
    }
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

private const val KAKAO_LINK_COMMIT_CREATED_AT_GRACE_SECONDS = 5L
private const val KAKAO_LINK_SPEC_SEND_ATTEMPTS = 3
