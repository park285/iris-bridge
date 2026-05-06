package party.qwer.iris.imagebridge.runtime

import java.lang.reflect.Method

internal fun selectReplyMarkdownRequestHookMethodForTest(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): Method? = selectReplyMarkdownRequestHookMethod(companionClass, chatRoomClass, writeTypeClass, listenerClass)

internal fun selectReplyMarkdownRequestHookMethodsForTest(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): List<Method> = selectReplyMarkdownRequestHookMethods(companionClass, chatRoomClass, writeTypeClass, listenerClass)

private fun selectReplyMarkdownRequestHookMethod(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): Method? = selectReplyMarkdownRequestHookMethods(companionClass, chatRoomClass, writeTypeClass, listenerClass).firstOrNull()

internal fun selectReplyMarkdownRequestHookMethods(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): List<Method> =
    companionClass.methods
        .asSequence()
        .filter { method ->
            method.parameterCount == 5 &&
                method.parameterTypes[4] == Boolean::class.javaPrimitiveType &&
                matchesReplyMarkdownRequestTypes(method, chatRoomClass, writeTypeClass, listenerClass)
        }.sortedWith(
            compareBy<Method> { if (it.name == "u") 0 else 1 }
                .thenBy { it.name }
                .thenBy { it.toGenericString() },
        ).toList()

private fun matchesReplyMarkdownRequestTypes(
    method: Method,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): Boolean {
    if (chatRoomClass == null || writeTypeClass == null || listenerClass == null) return method.name == "u"
    val parameterTypes = method.parameterTypes
    return parameterTypes[0].isAssignableFrom(chatRoomClass) &&
        parameterTypes[2].isAssignableFrom(writeTypeClass) &&
        parameterTypes[3].isAssignableFrom(listenerClass)
}
