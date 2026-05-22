package party.qwer.iris.imagebridge.runtime

import android.util.Log
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_LEVERAGE_COMMIT
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private const val CHAT_SENDING_LOG_REQUEST_CLASS = "com.kakao.talk.manager.send.ChatSendingLogRequest"

internal fun selectReplyLeverageChatLogCommitHookMethods(classLoader: ClassLoader): List<Method> {
    val requestClass = Class.forName(CHAT_SENDING_LOG_REQUEST_CLASS, false, classLoader)
    return requestClass.declaredMethods
        .asSequence()
        .filter { method ->
            method.name == "I" &&
                method.parameterCount == 1 &&
                method.returnType == Void.TYPE &&
                !Modifier.isAbstract(method.modifiers)
        }.sortedBy { method -> method.toGenericString() }
        .toList()
}

internal fun handleReplyLeverageChatLogCommitArgs(
    request: Any?,
    args: Array<Any?>,
    tag: String,
    leveragePendingContexts: ReplyLeveragePendingContextStore,
) {
    val chatLog = args.getOrNull(0) ?: return
    val sendingLog = request?.let(::readRequestSendingLog)
    val roomId =
        sendingLog?.let(ReplyMarkdownSendingLogAccess::readRoomId)
            ?: readLongNoArg(chatLog, "getChatRoomId")
            ?: return
    val currentSessionId = sendingLog?.let(ReplyMarkdownSendingLogAccess::readAttachmentSessionId)
    val messageText = sendingLog?.let(ReplyMarkdownSendingLogAccess::readMessageText) ?: readChatLogMessageText(chatLog)
    val leverageContext =
        if (messageText.isNullOrBlank()) {
            leveragePendingContexts.matchLatest(roomId, currentSessionId)
        } else {
            leveragePendingContexts.match(roomId, messageText, currentSessionId)
                ?: leveragePendingContexts.matchLatest(roomId, currentSessionId)
        } ?: return
    runCatching {
        val generatedAttachment = readChatLogAttachmentText(chatLog)
        val attachmentText = mergeLeverageAttachment(generatedAttachment, leverageContext.attachmentText)
        writeChatLogAttachmentText(chatLog, attachmentText)
        safeLogInfo(
            tag,
            "reply leverage chatlog attachment injected room=${leverageContext.roomId} " +
                "generated=${generatedAttachment != null}",
        )
        defaultBridgeDiscovery.recordHook(
            HOOK_REPLY_LEVERAGE_COMMIT,
            "room=${leverageContext.roomId} leverage=true threadId=${leverageContext.threadId} scope=${leverageContext.threadScope}",
        )
    }.onFailure { error ->
        safeLogError(tag, "reply leverage chatlog attachment injection failed room=${leverageContext.roomId}", error)
    }
}

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
