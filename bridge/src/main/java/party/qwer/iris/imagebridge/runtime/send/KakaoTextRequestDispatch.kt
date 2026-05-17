package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun selectTextRequestMethodForTest(
    companionClass: Class<*>,
    registry: KakaoClassRegistry,
): Method = selectTextRequestMethod(companionClass, registry)

internal fun selectTextRequestMethod(
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

internal fun resolveRequestTarget(
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

internal fun selectTextMessageType(registry: KakaoClassRegistry): Any =
    registry.messageTypeClass.enumConstants
        ?.firstOrNull { constant -> constant.toString().equals("Text", ignoreCase = true) }
        ?: error("message type Text not found")

internal fun selectLeverageMessageType(registry: KakaoClassRegistry): Any =
    registry.messageTypeClass.enumConstants
        ?.firstOrNull { constant -> constant.toString().equals("Leverage", ignoreCase = true) }
        ?: error("message type Leverage not found")

internal fun selectTextWriteType(): Any? =
    // Kakao ShareManager 일반 텍스트 경로는 writeType enum이 아니라 null을 넘긴다.
    null

internal fun selectLeverageSchemeWriteType(registry: KakaoClassRegistry): Any? = selectConnectWriteType(registry) ?: selectWriteTypeByName(registry, "LeverageScheme")

internal fun selectConnectWriteType(registry: KakaoClassRegistry): Any? = selectWriteTypeByName(registry, "Connect")

private fun selectWriteTypeByName(
    registry: KakaoClassRegistry,
    name: String,
): Any? =
    registry.writeTypeClass.enumConstants?.firstOrNull { constant ->
        constant.toString().equals(name, ignoreCase = true)
    }
