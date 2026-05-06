package party.qwer.iris.imagebridge.runtime.send

import android.content.Context
import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore

internal class KakaoTextSendInvocationFactory(
    private val registry: KakaoClassRegistry,
    private val context: Context? = null,
    private val mentionPendingContexts: ReplyMentionPendingContextStore? = null,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
    private val requestCompanionClassProvider: () -> Class<*> = {
        Class.forName(
            REQUEST_COMPANION_CLASS,
            false,
            registry.chatRoomClass.classLoader,
        )
    },
) : KakaoTextSendInvoker {
    private val bindingResult: Result<KakaoTextSendBinding> by lazy { runCatching { discoverTextSendBinding() } }

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
        requestId: String?,
    ) {
        val binding = bindingResult.getOrThrow()
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "text send invoking requestId=$requestId room=$roomId markdown=$markdown " +
                "threadId=$threadId messageLength=${message.length} mentions=${!mentionsJson.isNullOrBlank()}",
        )
        val replyAttachment = buildReplyAttachment(markdown, mentionsJson, requestId)
        if (canUseShareManagerTextPath(markdown, threadId, threadScope, binding.shareManagerTextInvoker)) {
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
        val sendingLog =
            binding.sendingLogFactory.newSendingLog(
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

    private fun discoverTextSendBinding(): KakaoTextSendBinding {
        val companionClass = requestCompanionClassProvider()
        val requestMethod = selectTextRequestMethod(companionClass, registry)
        val requestTarget = resolveRequestTarget(companionClass, requestMethod)
        val sendingLogClass = requestMethod.parameterTypes[1]
        val sendingLogFactory =
            discoverSendingLogFactory(
                sendingLogClass = sendingLogClass,
                messageType = selectTextMessageType(registry),
                logInfo = logInfo,
                chatRoomClass = registry.chatRoomClass,
                origin = resolveTextSendingLogOrigin(registry.chatRoomClass.classLoader),
            )
        logInfo(
            KAKAO_TEXT_SEND_TAG,
            "text send discovery companion=${companionClass.name} requestMethod=${requestMethod.toGenericString()} " +
                "sendingLogClass=${sendingLogClass.name}",
        )
        return KakaoTextSendBinding(
            requestMethod = requestMethod,
            requestTarget = requestTarget,
            sendingLogFactory = sendingLogFactory,
            shareManagerTextInvoker =
                discoverShareManagerTextInvoker(
                    context = context,
                    chatRoomClass = registry.chatRoomClass,
                    listenerClass = registry.listenerClass,
                    logInfo = logInfo,
                ),
            writeType = selectTextWriteType(),
            listener =
                createShareManagerSendListener(
                    context = context,
                    loader = registry.chatRoomClass.classLoader,
                    listenerClass = registry.listenerClass,
                    logInfo = logInfo,
                ),
        )
    }

    private companion object {
        private const val REQUEST_COMPANION_CLASS = "com.kakao.talk.manager.send.ChatSendingLogRequest\$a"
    }
}
