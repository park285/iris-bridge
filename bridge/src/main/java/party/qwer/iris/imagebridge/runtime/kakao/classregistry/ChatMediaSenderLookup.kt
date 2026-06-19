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
            isChatMediaSenderConstructor(ctor.parameterTypes, function0Class, function1Class)
        } &&
        methodsInHierarchy(clazz).any { method ->
            isMultiSendMethod(method, messageTypeClass)
        }

private fun isChatMediaSenderConstructor(
    parameterTypes: Array<Class<*>>,
    function0Class: Class<*>,
    function1Class: Class<*>,
): Boolean =
    isLegacyChatMediaSenderConstructor(parameterTypes, function0Class, function1Class) ||
        isModernChatMediaSenderConstructor(parameterTypes, function1Class)

private fun isLegacyChatMediaSenderConstructor(
    parameterTypes: Array<Class<*>>,
    function0Class: Class<*>,
    function1Class: Class<*>,
): Boolean =
    parameterTypes.size == 4 &&
        isThreadIdParameterType(parameterTypes[1]) &&
        parameterTypes[2] == function0Class &&
        parameterTypes[3] == function1Class

private fun isModernChatMediaSenderConstructor(
    parameterTypes: Array<Class<*>>,
    function1Class: Class<*>,
): Boolean =
    parameterTypes.size == 3 &&
        isThreadIdParameterType(parameterTypes[1]) &&
        parameterTypes[2] == function1Class

private fun isThreadIdParameterType(parameterType: Class<*>): Boolean =
    parameterType == java.lang.Long::class.java || parameterType == java.lang.Long.TYPE

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
