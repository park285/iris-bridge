@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao

import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KakaoClassRegistryDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.matchesChatMediaSenderClass
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.resolveChatMediaSendMethods
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.selectClassCandidate
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.selectMethodCandidate
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal class KakaoClassRegistry(
    val mediaItemClass: Class<*>,
    val function0Class: Class<*>,
    val function1Class: Class<*>,
    val masterDatabaseClass: Class<*>,
    val writeTypeClass: Class<*>,
    val listenerClass: Class<*>,
    val chatMediaSenderClass: Class<*>,
    val messageTypeClass: Class<*>,
    val chatRoomManagerClass: Class<*>,
    val chatRoomClass: Class<*>,
    val singleSendMethod: Method,
    val multiSendMethod: Method,
    val mediaItemConstructor: Constructor<*>,
    val masterDbSingletonField: Field,
    val roomDaoMethod: Method,
    val entityLookupMethod: Method,
    val broadRoomResolverMethod: Method,
    val directRoomResolverMethod: Method,
    val photoType: Any,
    val multiPhotoType: Any,
    val writeTypeNone: Any,
) {
    companion object {
        fun discover(classLoader: ClassLoader): KakaoClassRegistry = KakaoClassRegistryDiscovery.discover(classLoader)

        internal fun selectMethodCandidateForTest(
            label: String,
            candidates: List<Method>,
            preferredNames: Set<String> = emptySet(),
        ): Method = selectMethodCandidate(label, candidates, preferredNames)

        internal fun selectChatMediaSenderCandidateForTest(
            candidates: List<Class<*>>,
            mediaItemClass: Class<*>,
            function0Class: Class<*>,
            function1Class: Class<*>,
        ): Class<*> =
            selectClassCandidate(
                label = "ChatMediaSender",
                candidates =
                    candidates.filter { candidate ->
                        matchesChatMediaSenderClass(
                            clazz = candidate,
                            mediaItemClass = mediaItemClass,
                            function0Class = function0Class,
                            function1Class = function1Class,
                        )
                    },
                preferredNames = emptySet(),
            )

        internal fun resolveChatMediaSenderMethodsForTest(
            chatMediaSenderClass: Class<*>,
            mediaItemClass: Class<*>,
            messageTypeClass: Class<*>,
        ): Pair<Method, Method> =
            resolveChatMediaSendMethods(
                chatMediaSenderClass = chatMediaSenderClass,
                mediaItemClass = mediaItemClass,
                messageTypeClass = messageTypeClass,
            )
    }
}
