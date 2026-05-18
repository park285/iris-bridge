package party.qwer.iris.imagebridge.runtime.send

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore

internal class KakaoTextSendInvocationFactory(
    private val registry: KakaoClassRegistry,
    private val context: Context? = null,
    private val mentionPendingContexts: ReplyMentionPendingContextStore? = null,
    private val leveragePendingContexts: ReplyLeveragePendingContextStore? = null,
    private val leverageCommitPendingContexts: ReplyLeveragePendingContextStore? = null,
    private val kakaoLinkSpecSender: KakaoLinkSpecSender? = null,
    private val leverageAttachmentPatcher: KakaoLeverageAttachmentPatcher? = null,
    private val kakaoLinkCommitVerifier: KakaoChatLogCommitVerifier? = null,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
    private val requestCompanionClassProvider: () -> Class<*> = {
        Class.forName(
            REQUEST_COMPANION_CLASS,
            false,
            registry.chatRoomClass.classLoader,
        )
    },
) : KakaoTextSendInvoker {
    private val bindingResult: Result<KakaoTextSendBinding> by lazy {
        runCatching {
            discoverKakaoTextSendBinding(
                registry = registry,
                context = context,
                logInfo = logInfo,
                requestCompanionClassProvider = requestCompanionClassProvider,
            )
        }
    }

    override fun capability(): KakaoTextSendCapability =
        bindingResult.fold(
            onSuccess = { KakaoTextSendCapability(supported = true, ready = true) },
            onFailure = { error ->
                KakaoTextSendCapability(
                    supported = false,
                    ready = false,
                    reason = error.message ?: error.javaClass.name,
                )
            },
        )

    override fun send(
        roomId: Long,
        chatRoom: Any,
        message: String,
        markdown: Boolean,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String?,
        attachmentJson: String?,
        requestId: String?,
    ) {
        val binding = bindingResult.getOrThrow()
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "text send invoking requestId=$requestId room=$roomId markdown=$markdown " +
                "threadId=$threadId messageLength=${message.length} mentions=${!mentionsJson.isNullOrBlank()} " +
                "attachment=${!attachmentJson.isNullOrBlank()}",
        )
        val rawAttachment = attachmentJson?.trim()?.takeIf { it.isNotEmpty() }
        val replyAttachment = rawAttachment ?: buildReplyAttachment(markdown, mentionsJson, requestId)
        if (
            rawAttachment != null &&
            trySendWithLeveragePaths(
                binding = binding,
                chatRoom = chatRoom,
                roomId = roomId,
                message = message,
                threadId = threadId,
                threadScope = threadScope,
                rawAttachment = rawAttachment,
                requestId = requestId,
                leveragePendingContexts = leveragePendingContexts,
                leverageCommitPendingContexts = leverageCommitPendingContexts,
                kakaoLinkSpecSender = kakaoLinkSpecSender,
                leverageAttachmentPatcher = leverageAttachmentPatcher,
                kakaoLinkCommitVerifier = kakaoLinkCommitVerifier,
                logInfo = logInfo,
            )
        ) {
            return
        }
        if (rawAttachment == null && canUseShareManagerTextPath(markdown, threadId, threadScope, binding.shareManagerTextInvoker)) {
            if (
                sendWithShareManagerTextPath(
                    chatRoom = chatRoom,
                    roomId = roomId,
                    message = message,
                    replyAttachment = replyAttachment,
                    requestId = requestId,
                    shareManagerTextInvoker = binding.shareManagerTextInvoker,
                    mentionPendingContexts = mentionPendingContexts,
                    logInfo = logInfo,
                )
            ) {
                return
            }
        }
        val sendingLogFactory = if (rawAttachment != null) binding.leverageSendingLogFactory else binding.sendingLogFactory
        val sendingLog =
            sendingLogFactory.newSendingLog(
                roomId = roomId,
                chatRoom = chatRoom,
                message = message,
                markdown = markdown,
                mentionsJson = mentionsJson,
                requestId = requestId,
                replyAttachment = replyAttachment,
            )
        if (threadId != null && threadScope != null) {
            ReplyMarkdownSendingLogAccess.writeThreadMetadata(sendingLog, threadId, threadScope)
            logInfo(
                KAKAO_TEXT_SEND_TAG,
                "text send thread metadata writable requestId=$requestId threadId=$threadId scope=$threadScope",
            )
        }
        binding.invoke(chatRoom, sendingLog)
    }

    private companion object {
        private const val REQUEST_COMPANION_CLASS = "com.kakao.talk.manager.send.ChatSendingLogRequest\$a"
    }
}
