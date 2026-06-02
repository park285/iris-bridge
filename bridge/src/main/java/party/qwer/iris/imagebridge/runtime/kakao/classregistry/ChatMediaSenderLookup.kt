@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun matchesChatMediaSenderClass(
    clazz: Class<*>,
    messageTypeClass: Class<*>,
    function0Class: Class<*>,
    function1Class: Class<*>,
): Boolean =
    isConcreteClass(clazz) &&
        clazz.constructors.any { ctor ->
            ctor.parameterTypes.size == 4 &&
                ctor.parameterTypes[1] == java.lang.Long::class.java &&
                ctor.parameterTypes[2] == function0Class &&
                ctor.parameterTypes[3] == function1Class
        } &&
        methodsInHierarchy(clazz).any { method ->
            isMultiSendMethod(method, messageTypeClass)
        }

internal fun resolveChatMediaSendMethods(
    chatMediaSenderClass: Class<*>,
    mediaItemClass: Class<*>?,
    messageTypeClass: Class<*>,
): Pair<Method?, Method> {
    val allMethods = methodsInHierarchy(chatMediaSenderClass)
    val singleSendCandidates =
        if (mediaItemClass == null) {
            emptyList()
        } else {
            allMethods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == mediaItemClass &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType
            }
        }
    val singleSend =
        singleSendCandidates.takeIf { it.isNotEmpty() }?.let { candidates ->
            selectMethodCandidate(
                label = "ChatMediaSender single send on ${chatMediaSenderClass.name}",
                candidates = candidates,
                preferredNames = setOf("n"),
            )
        }
    val multiSend =
        selectMethodCandidate(
            label = "ChatMediaSender multi send on ${chatMediaSenderClass.name}",
            candidates =
                allMethods.filter { method ->
                    isMultiSendMethod(method, messageTypeClass)
                },
            preferredNames = setOf("p"),
        )
    return singleSend to multiSend
}

private fun isMultiSendMethod(
    method: Method,
    messageTypeClass: Class<*>,
): Boolean =
    !Modifier.isStatic(method.modifiers) &&
        method.parameterCount == 9 &&
        method.parameterTypes[0] == List::class.java &&
        method.parameterTypes[1] == messageTypeClass

private fun methodsInHierarchy(clazz: Class<*>): List<Method> {
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
