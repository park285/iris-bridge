package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.selectReplyMarkdownRequestHookMethodForTest
import java.lang.reflect.Method

internal fun selectThreadedImageInjectBindingsForTest(
    requestCompanionClass: Class<*>?,
    chatMediaSenderClass: Class<*>,
    chatRoomClass: Class<*>,
    writeTypeClass: Class<*>,
    listenerClass: Class<*>,
): List<ThreadedImageXposedInjector.InjectHookBinding> =
    selectThreadedImageInjectBindings(
        requestCompanionClass = requestCompanionClass,
        chatMediaSenderClass = chatMediaSenderClass,
        chatRoomClass = chatRoomClass,
        writeTypeClass = writeTypeClass,
        listenerClass = listenerClass,
    )

internal fun selectThreadedImageInjectMethodForTest(
    chatMediaSenderClass: Class<*>,
    writeTypeClass: Class<*>,
    listenerClass: Class<*>,
): Method =
    selectLegacyThreadedImageInjectMethod(
        chatMediaSenderClass = chatMediaSenderClass,
        writeTypeClass = writeTypeClass,
        listenerClass = listenerClass,
    )

internal fun selectThreadedImageInjectBindings(
    requestCompanionClass: Class<*>?,
    chatMediaSenderClass: Class<*>,
    chatRoomClass: Class<*>,
    writeTypeClass: Class<*>,
    listenerClass: Class<*>,
): List<ThreadedImageXposedInjector.InjectHookBinding> =
    buildList {
        requestCompanionClass?.let { companionClass ->
            selectReplyMarkdownRequestHookMethodForTest(companionClass, chatRoomClass, writeTypeClass, listenerClass)
                ?.let { method ->
                    add(ThreadedImageXposedInjector.InjectHookBinding(method, sendingLogArgIndex = 1, source = "request"))
                }
        }
        add(
            ThreadedImageXposedInjector.InjectHookBinding(
                method = selectLegacyThreadedImageInjectMethod(chatMediaSenderClass, writeTypeClass, listenerClass),
                sendingLogArgIndex = 0,
                source = "legacy",
            ),
        )
    }

private fun selectLegacyThreadedImageInjectMethod(
    chatMediaSenderClass: Class<*>,
    writeTypeClass: Class<*>,
    listenerClass: Class<*>,
): Method =
    KakaoClassRegistry.selectMethodCandidateForTest(
        label = "ChatMediaSender threaded inject on ${chatMediaSenderClass.name}",
        candidates =
            collectMethodsInHierarchy(chatMediaSenderClass).filter { method ->
                !java.lang.reflect.Modifier
                    .isStatic(method.modifiers) &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[1] == writeTypeClass &&
                    method.parameterTypes[2] == listenerClass
            },
        preferredNames = setOf("A"),
    )

private fun collectMethodsInHierarchy(clazz: Class<*>): List<Method> {
    val methods = mutableListOf<Method>()
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        current.declaredMethods.forEach { method ->
            runCatching { method.isAccessible = true }
            methods += method
        }
        current = current.superclass
    }
    return methods
}
