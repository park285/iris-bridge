@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun matchesChatMediaSenderClass(
    clazz: Class<*>,
    mediaItemClass: Class<*>,
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
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == mediaItemClass &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }

internal fun resolveChatMediaSendMethods(
    chatMediaSenderClass: Class<*>,
    mediaItemClass: Class<*>,
    messageTypeClass: Class<*>,
): Pair<Method, Method> {
    val singleSend =
        selectMethodCandidate(
            label = "ChatMediaSender single send on ${chatMediaSenderClass.name}",
            candidates =
                methodsInHierarchy(chatMediaSenderClass).filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == mediaItemClass &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType
                },
            preferredNames = setOf("n"),
        )
    val multiSend =
        selectMethodCandidate(
            label = "ChatMediaSender multi send on ${chatMediaSenderClass.name}",
            candidates =
                methodsInHierarchy(chatMediaSenderClass).filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 9 &&
                        method.parameterTypes[0] == List::class.java &&
                        method.parameterTypes[1] == messageTypeClass
                },
            preferredNames = setOf("p"),
        )
    return singleSend to multiSend
}

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
