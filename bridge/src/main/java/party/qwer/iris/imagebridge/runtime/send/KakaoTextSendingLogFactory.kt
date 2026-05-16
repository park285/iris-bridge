package party.qwer.iris.imagebridge.runtime.send

import org.json.JSONObject
import party.qwer.iris.ReplyAttachmentProtocol
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess

internal interface KakaoSendingLogFactory {
    fun newSendingLog(
        roomId: Long,
        chatRoom: Any,
        message: String,
        markdown: Boolean,
        mentionsJson: String?,
        requestId: String?,
        replyAttachment: String? = buildReplyAttachment(markdown, mentionsJson, requestId),
    ): Any
}

internal data class KakaoSendingLogOrigin(
    val sourceClass: Class<*>,
    val tag: String,
)

internal fun resolveTextSendingLogOrigin(loader: ClassLoader?): KakaoSendingLogOrigin {
    val shareManagerClass =
        runCatching {
            Class.forName(SHARE_MANAGER_CLASS, false, loader)
        }.getOrNull()
    return if (shareManagerClass != null) {
        KakaoSendingLogOrigin(shareManagerClass, "FM")
    } else {
        KakaoSendingLogOrigin(KakaoTextSendInvocationFactory::class.java, "send")
    }
}

internal fun discoverSendingLogFactory(
    sendingLogClass: Class<*>,
    messageType: Any,
    logInfo: (String, String) -> Unit,
    chatRoomClass: Class<*>,
    origin: KakaoSendingLogOrigin,
): KakaoSendingLogFactory {
    val builderClass =
        sendingLogClass.declaredClasses
            .firstOrNull { clazz -> clazz.simpleName == "b" || clazz.simpleName.equals("Builder", ignoreCase = true) }
            ?: error("ChatSendingLog builder class not found")
    val constructor = selectBuilderConstructor(builderClass, messageType.javaClass, chatRoomClass)
    val buildMethod = selectBuildMethod(builderClass, sendingLogClass)
    val messageMethod =
        selectBuilderMethod(builderClass, String::class.java, preferredNames = setOf("j"))
            ?: error("ChatSendingLog builder message method not found")
    val tagMethod = selectBuilderMethod(builderClass, Class::class.java, String::class.java, preferredNames = setOf("l"))
    val attachmentMethod = selectBuilderMethod(builderClass, JSONObject::class.java, preferredNames = setOf("c"))
    logInfo(
        KAKAO_TEXT_SEND_TAG,
        "text send discovery builder=${builderClass.name} constructor=${constructor.constructor.toGenericString()} " +
            "buildMethod=${buildMethod.toGenericString()} messageMethod=${messageMethod.toGenericString()} " +
            "tagMethod=${tagMethod?.toGenericString() ?: "<missing>"} " +
            "attachmentMethod=${attachmentMethod?.toGenericString() ?: "<missing>"}",
    )

    return object : KakaoSendingLogFactory {
        override fun newSendingLog(
            roomId: Long,
            chatRoom: Any,
            message: String,
            markdown: Boolean,
            mentionsJson: String?,
            requestId: String?,
            replyAttachment: String?,
        ): Any {
            val builder = newBuilder(constructor, roomId, chatRoom, messageType)
            messageMethod.apply { isAccessible = true }.invoke(builder, message)
            tagMethod?.apply { isAccessible = true }?.invoke(builder, origin.sourceClass, origin.tag)
            replyAttachment?.let { attachment ->
                attachmentMethod?.apply { isAccessible = true }?.invoke(builder, JSONObject(attachment))
            }
            val sendingLog =
                requireNotNull(buildMethod.apply { isAccessible = true }.invoke(builder)) {
                    "sendingLog builder returned null"
                }
            require(ReplyMarkdownSendingLogAccess.readRoomId(sendingLog) == roomId) { "sendingLog room validation failed" }
            require(ReplyMarkdownSendingLogAccess.readMessageText(sendingLog) == message) { "sendingLog message validation failed" }
            logInfo(KAKAO_TEXT_SEND_TAG, "text send sendingLog validation ok room=$roomId messageLength=${message.length}")
            replyAttachment?.let { attachment ->
                ReplyMarkdownSendingLogAccess.writeAttachmentText(sendingLog, attachment)
                logInfo(KAKAO_TEXT_SEND_TAG, "text send attachment writable requestId=$requestId")
            }
            return sendingLog
        }
    }
}

internal fun buildReplyAttachment(
    markdown: Boolean,
    mentionsJson: String?,
    requestId: String?,
): String? =
    ReplyAttachmentProtocol.build(
        markdown = markdown,
        mentionsJson = mentionsJson,
        sessionId = requestId,
    )
