package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore

internal fun runKakaoLinkSpecPathOrFalse(
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

internal fun sendWithKakaoLinkSpecPath(
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
        rememberLeverageContext(
            roomId,
            message,
            threadId,
            threadScope,
            rawAttachment,
            requestId,
            leverageCommitPendingContexts,
            logInfo,
            "commit",
        )
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
        return sendServerGeneratedKakaoLinkSpecPath(
            sender,
            verifier,
            KakaoLinkSpecCommitAttempt(
                roomId,
                chatRoom,
                message,
                rawAttachment,
                requestId,
                sendAttachment,
                commitVerificationAttachment,
                minimumCreatedAt,
                minimumRowId,
            ),
            logInfo,
        )
    }
    if (!sender.send(roomId, chatRoom, message, sendAttachment, requestId)) {
        return false
    }
    (leverageAttachmentPatcher ?: binding.leverageAttachmentPatcher)?.patchAsync(roomId, message, rawAttachment, requestId)
    return true
}

private fun sendServerGeneratedKakaoLinkSpecPath(
    sender: KakaoLinkSpecSender,
    verifier: KakaoChatLogCommitVerifier?,
    attempt: KakaoLinkSpecCommitAttempt,
    logInfo: (String, String) -> Unit,
): Boolean {
    var sentAtLeastOnce = false
    repeat(KAKAO_LINK_SPEC_SEND_ATTEMPTS) { index ->
        if (!sendKakaoLinkSpecAttempt(sender, attempt, index, logInfo)) {
            return@repeat
        }
        sentAtLeastOnce = true
        if (verifier?.awaitCommitted(
                attempt.roomId,
                attempt.message,
                attempt.minimumCreatedAt,
                attempt.minimumRowId,
                attempt.requestId,
                attempt.commitVerificationAttachment,
            ) ==
            true
        ) {
            if (!verifier.cleanupPendingKakaoLinkSendingLogs(
                    attempt.roomId,
                    attempt.minimumCreatedAt,
                    attempt.requestId,
                    attempt.commitVerificationAttachment,
                )
            ) {
                logInfo(
                    KAKAO_TEXT_SEND_TAG,
                    "kakaolink pending sending log cleanup failed after chat log commit " +
                        "requestId=${attempt.requestId} room=${attempt.roomId}",
                )
            }
            return true
        }
        logMissingKakaoLinkCommit(index, attempt, logInfo)
    }
    if (verifier?.cleanupPendingKakaoLinkSendingLogs(
            attempt.roomId,
            attempt.minimumCreatedAt,
            attempt.requestId,
            attempt.commitVerificationAttachment,
        ) ==
        false
    ) {
        error("KakaoLinkSpec pending sending log cleanup failed before chat log commit")
    }
    if (!sentAtLeastOnce) {
        error("KakaoLinkSpec server template send failed before chat log commit")
    }
    error("KakaoLinkSpec server template send did not create chat log")
}

private fun sendKakaoLinkSpecAttempt(
    sender: KakaoLinkSpecSender,
    attempt: KakaoLinkSpecCommitAttempt,
    index: Int,
    logInfo: (String, String) -> Unit,
): Boolean {
    if (sender.send(attempt.roomId, attempt.chatRoom, attempt.message, attempt.sendAttachment, attempt.requestId)) {
        return true
    }
    logInfo(
        KAKAO_TEXT_SEND_TAG,
        "kakaolink spec send returned false requestId=${attempt.requestId} room=${attempt.roomId} attempt=${index + 1}",
    )
    return false
}

private fun logMissingKakaoLinkCommit(
    index: Int,
    attempt: KakaoLinkSpecCommitAttempt,
    logInfo: (String, String) -> Unit,
) {
    if (index + 1 < KAKAO_LINK_SPEC_SEND_ATTEMPTS) {
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "kakaolink spec commit missing; retrying requestId=${attempt.requestId} room=${attempt.roomId} attempt=${index + 1}",
        )
    }
}

private data class KakaoLinkSpecCommitAttempt(
    val roomId: Long,
    val chatRoom: Any,
    val message: String,
    val rawAttachment: String,
    val requestId: String?,
    val sendAttachment: String,
    val commitVerificationAttachment: String,
    val minimumCreatedAt: Long,
    val minimumRowId: Long,
)

private const val KAKAO_LINK_COMMIT_CREATED_AT_GRACE_SECONDS = 5L
private const val KAKAO_LINK_SPEC_SEND_ATTEMPTS = 1
