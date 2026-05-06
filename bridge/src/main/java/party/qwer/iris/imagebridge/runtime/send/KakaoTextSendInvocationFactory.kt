package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import party.qwer.iris.ReplyAttachmentProtocol
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

internal data class KakaoTextSendCapability(
    val supported: Boolean,
    val ready: Boolean,
    val reason: String? = null,
)

internal interface KakaoTextSendInvoker {
    fun capability(): KakaoTextSendCapability

    fun send(
        roomId: Long,
        chatRoom: Any,
        message: String,
        markdown: Boolean,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String?,
        requestId: String?,
    )
}

internal class KakaoTextSendInvocationFactory(
    private val registry: KakaoClassRegistry,
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
            TAG,
            "text send invoking requestId=$requestId room=$roomId markdown=$markdown " +
                "threadId=$threadId messageLength=${message.length} mentions=${!mentionsJson.isNullOrBlank()}",
        )
        val sendingLog =
            binding.sendingLogFactory.newSendingLog(
                roomId = roomId,
                message = message,
                markdown = markdown,
                mentionsJson = mentionsJson,
                requestId = requestId,
            )
        if (threadId != null && threadScope != null) {
            ReplyMarkdownSendingLogAccess.writeThreadMetadata(sendingLog, threadId, threadScope)
            logInfo(TAG, "text send thread metadata writable requestId=$requestId threadId=$threadId scope=$threadScope")
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
            )
        logInfo(
            TAG,
            "text send discovery companion=${companionClass.name} requestMethod=${requestMethod.toGenericString()} " +
                "sendingLogClass=${sendingLogClass.name}",
        )
        return KakaoTextSendBinding(
            requestMethod = requestMethod,
            requestTarget = requestTarget,
            sendingLogFactory = sendingLogFactory,
            writeType = selectConnectWriteType(registry),
            listener = createNoopListener(registry.listenerClass),
        )
    }

    private companion object {
        private const val REQUEST_COMPANION_CLASS = "com.kakao.talk.manager.send.ChatSendingLogRequest\$a"
        private const val TAG = "IrisBridge"
    }
}

internal data class KakaoTextSendBinding(
    val requestMethod: Method,
    val requestTarget: Any?,
    val sendingLogFactory: KakaoSendingLogFactory,
    val writeType: Any,
    val listener: Any?,
) {
    fun invoke(
        chatRoom: Any,
        sendingLog: Any,
    ) {
        requestMethod.apply { isAccessible = true }.invoke(
            requestTarget,
            chatRoom,
            sendingLog,
            writeType,
            listener,
            false,
        )
    }
}

internal interface KakaoSendingLogFactory {
    fun newSendingLog(
        roomId: Long,
        message: String,
        markdown: Boolean,
        mentionsJson: String?,
        requestId: String?,
    ): Any
}

internal fun selectTextRequestMethodForTest(
    companionClass: Class<*>,
    registry: KakaoClassRegistry,
): Method = selectTextRequestMethod(companionClass, registry)

private fun selectTextRequestMethod(
    companionClass: Class<*>,
    registry: KakaoClassRegistry,
): Method =
    KakaoClassRegistry.selectMethodCandidateForTest(
        label = "ChatSendingLogRequest direct text dispatch",
        candidates =
            companionClass.methods.filter { method ->
                method.parameterCount == 5 &&
                    method.parameterTypes[0].isAssignableFrom(registry.chatRoomClass) &&
                    method.parameterTypes[2].isAssignableFrom(registry.writeTypeClass) &&
                    method.parameterTypes[3].isAssignableFrom(registry.listenerClass) &&
                    method.parameterTypes[4] == Boolean::class.javaPrimitiveType
            },
        preferredNames = setOf("u"),
    )

private fun resolveRequestTarget(
    companionClass: Class<*>,
    method: Method,
): Any? {
    if (Modifier.isStatic(method.modifiers)) return null
    return requestTargetOwners(companionClass)
        .flatMap { owner ->
            owner.declaredFields
                .asSequence()
                .filter { field -> Modifier.isStatic(field.modifiers) && companionClass.isAssignableFrom(field.type) }
        }.mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                field.get(null)
            }.getOrNull()
        }.firstOrNull()
        ?: error("ChatSendingLogRequest companion instance not found")
}

private fun requestTargetOwners(companionClass: Class<*>): Sequence<Class<*>> =
    sequenceOf(companionClass, companionClass.enclosingClass)
        .filterNotNull()
        .distinct()

private fun selectTextMessageType(registry: KakaoClassRegistry): Any =
    registry.messageTypeClass.enumConstants
        ?.firstOrNull { constant -> constant.toString().equals("Text", ignoreCase = true) }
        ?: error("message type Text not found")

private fun selectConnectWriteType(registry: KakaoClassRegistry): Any =
    registry.writeTypeClass.enumConstants
        ?.firstOrNull { constant -> constant.toString().equals("Connect", ignoreCase = true) }
        ?: registry.writeTypeNone

private fun createNoopListener(listenerClass: Class<*>): Any? =
    if (listenerClass.isInterface) {
        Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, _, _ -> null }
    } else {
        null
    }

private fun discoverSendingLogFactory(
    sendingLogClass: Class<*>,
    messageType: Any,
    logInfo: (String, String) -> Unit,
): KakaoSendingLogFactory {
    val builderClass =
        sendingLogClass.declaredClasses
            .firstOrNull { clazz -> clazz.simpleName == "b" || clazz.simpleName.equals("Builder", ignoreCase = true) }
            ?: error("ChatSendingLog builder class not found")
    val constructor = selectBuilderConstructor(builderClass, messageType.javaClass)
    val buildMethod = selectBuildMethod(builderClass, sendingLogClass)
    val messageMethod =
        selectBuilderMethod(builderClass, String::class.java, preferredNames = setOf("j"))
            ?: error("ChatSendingLog builder message method not found")
    val tagMethod = selectBuilderMethod(builderClass, Class::class.java, String::class.java, preferredNames = setOf("l"))
    logInfo(
        "IrisBridge",
        "text send discovery builder=${builderClass.name} constructor=${constructor.toGenericString()} " +
            "buildMethod=${buildMethod.toGenericString()} messageMethod=${messageMethod.toGenericString()} " +
            "tagMethod=${tagMethod?.toGenericString() ?: "<missing>"}",
    )

    return object : KakaoSendingLogFactory {
        override fun newSendingLog(
            roomId: Long,
            message: String,
            markdown: Boolean,
            mentionsJson: String?,
            requestId: String?,
        ): Any {
            val builder = newBuilder(constructor, roomId, messageType)
            messageMethod.apply { isAccessible = true }.invoke(builder, message)
            tagMethod?.apply { isAccessible = true }?.invoke(builder, KakaoTextSendInvocationFactory::class.java, "send")
            val sendingLog = requireNotNull(buildMethod.apply { isAccessible = true }.invoke(builder)) { "sendingLog builder returned null" }
            require(ReplyMarkdownSendingLogAccess.readRoomId(sendingLog) == roomId) { "sendingLog room validation failed" }
            require(ReplyMarkdownSendingLogAccess.readMessageText(sendingLog) == message) { "sendingLog message validation failed" }
            logInfo("IrisBridge", "text send sendingLog validation ok room=$roomId messageLength=${message.length}")
            buildReplyAttachment(markdown, mentionsJson, requestId)?.let { attachment ->
                ReplyMarkdownSendingLogAccess.writeAttachmentText(sendingLog, attachment)
                logInfo("IrisBridge", "text send attachment writable requestId=$requestId")
            }
            return sendingLog
        }
    }
}

private fun selectBuilderConstructor(
    builderClass: Class<*>,
    messageTypeClass: Class<*>,
): Constructor<*> =
    builderClass.declaredConstructors
        .filter { constructor ->
            val types = constructor.parameterTypes
            types.size >= 5 &&
                types[0] == Long::class.javaPrimitiveType &&
                types[1].isAssignableFrom(messageTypeClass)
        }.minByOrNull { constructor -> constructor.parameterCount }
        ?: error("ChatSendingLog builder constructor not found")

private fun newBuilder(
    constructor: Constructor<*>,
    roomId: Long,
    messageType: Any,
): Any {
    val args =
        constructor.parameterTypes
            .mapIndexed { index, type ->
                when (index) {
                    0 -> roomId
                    1 -> messageType
                    2 -> 0
                    3 -> null
                    4 -> false
                    5 -> 28
                    else -> null
                }.coerceFor(type)
            }.toTypedArray()
    return constructor.apply { isAccessible = true }.newInstance(*args)
}

private fun Any?.coerceFor(type: Class<*>): Any? =
    when {
        this != null -> this
        type.isPrimitive && type == Boolean::class.javaPrimitiveType -> false
        type.isPrimitive && type == Int::class.javaPrimitiveType -> 0
        type.isPrimitive && type == Long::class.javaPrimitiveType -> 0L
        else -> null
    }

private fun selectBuildMethod(
    builderClass: Class<*>,
    sendingLogClass: Class<*>,
): Method =
    builderClass.methods
        .filter { method -> method.parameterCount == 0 && sendingLogClass.isAssignableFrom(method.returnType) }
        .minWithOrNull(compareBy<Method> { if (it.name == "b") 0 else 1 }.thenBy { it.name })
        ?: error("ChatSendingLog builder build method not found")

private fun selectBuilderMethod(
    builderClass: Class<*>,
    vararg parameterTypes: Class<*>,
    preferredNames: Set<String>,
): Method? =
    builderClass.methods
        .filter { method ->
            method.parameterTypes.toList() == parameterTypes.toList() &&
                builderClass.isAssignableFrom(method.returnType)
        }.minWithOrNull(compareBy<Method> { if (it.name in preferredNames) 0 else 1 }.thenBy { it.name })

private fun buildReplyAttachment(
    markdown: Boolean,
    mentionsJson: String?,
    requestId: String?,
): String? =
    ReplyAttachmentProtocol.build(
        markdown = markdown,
        mentionsJson = mentionsJson,
        sessionId = requestId,
    )
