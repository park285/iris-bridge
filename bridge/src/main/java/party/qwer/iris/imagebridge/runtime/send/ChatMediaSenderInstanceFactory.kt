@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.send

import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

internal class ChatMediaSenderInstanceFactory(
    private val registry: KakaoClassRegistry,
) {
    private val senderConstructorCache = ConcurrentHashMap<Class<*>, SenderConstructorBinding>()

    fun newSender(
        chatRoom: Any,
        threadId: Long?,
        threadScope: Int?,
    ): Any {
        val sendWithChatRoomInThread = threadId != null && threadScope == 3
        val loader = registry.chatMediaSenderClass.classLoader ?: error("no classLoader")
        val sendWithThreadProxy =
            Proxy.newProxyInstance(loader, arrayOf(registry.function0Class)) { _, method, _ ->
                when (method.name) {
                    "invoke" -> sendWithChatRoomInThread
                    "toString" -> "IrisBridgeSendInThread($sendWithChatRoomInThread)"
                    "hashCode" -> sendWithChatRoomInThread.hashCode()
                    "equals" -> false
                    else -> null
                }
            }
        val attachmentDecoratorProxy =
            Proxy.newProxyInstance(loader, arrayOf(registry.function1Class)) { _, method, args ->
                when (method.name) {
                    "invoke" -> args?.getOrNull(0) as? JSONObject
                    "toString" -> "IrisBridgeAttachmentDecorator"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> null
                }
            }
        val constructor =
            senderConstructorCache.computeIfAbsent(chatRoom.javaClass) { chatRoomClass ->
                resolveSenderConstructor(chatRoomClass)
            }
        val threadIdArgument =
            normalizedThreadIdArgument(constructor.constructor.parameterTypes[1], threadId)
        return when (constructor.shape) {
            SenderConstructorShape.LegacyWithThreadFlag ->
                constructor.constructor.apply { isAccessible = true }.newInstance(
                    chatRoom,
                    threadIdArgument,
                    sendWithThreadProxy,
                    attachmentDecoratorProxy,
                )

            SenderConstructorShape.ModernThreadId ->
                constructor.constructor.apply { isAccessible = true }.newInstance(
                    chatRoom,
                    threadIdArgument,
                    attachmentDecoratorProxy,
                )
        }
    }

    private fun resolveSenderConstructor(chatRoomClass: Class<*>): SenderConstructorBinding {
        val candidates =
            registry.chatMediaSenderClass.declaredConstructors.mapNotNull { constructor ->
                val parameterTypes = constructor.parameterTypes
                val shape =
                    senderConstructorShape(
                        parameterTypes,
                        registry.function0Class,
                        registry.function1Class,
                    ) ?: return@mapNotNull null
                if (
                    parameterTypes[0].isAssignableFrom(chatRoomClass) &&
                    isThreadIdParameterType(parameterTypes[1])
                ) {
                    SenderConstructorBinding(constructor, shape)
                } else {
                    null
                }
            }
        check(candidates.isNotEmpty()) {
            "ChatMediaSender constructor not found for chatRoom=${chatRoomClass.name}"
        }
        val exactCandidates = candidates.filter { candidate -> candidate.constructor.parameterTypes[0] == chatRoomClass }
        if (exactCandidates.isNotEmpty()) {
            return selectConstructorBinding(
                candidates = exactCandidates,
                ambiguousMessage = "ChatMediaSender exact constructor is ambiguous for chatRoom=${chatRoomClass.name}",
            )
        }

        val bestDistance = candidates.minOf { candidate -> typeDistance(chatRoomClass, candidate.constructor.parameterTypes[0]) }
        val bestCandidates =
            candidates.filter { candidate ->
                typeDistance(chatRoomClass, candidate.constructor.parameterTypes[0]) == bestDistance
            }
        return selectConstructorBinding(
            candidates = bestCandidates,
            ambiguousMessage = "ChatMediaSender constructor is ambiguous for chatRoom=${chatRoomClass.name}",
        )
    }
}
