@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun resolveChatRoomManagerAccessor(registry: KakaoClassRegistry): () -> Any {
    val managerClass = registry.chatRoomManagerClass
    val preferredAccessorNames = setOf("j", "G0", "I")
    val companion =
        managerClass.declaredFields
            .asSequence()
            .filter { Modifier.isStatic(it.modifiers) }
            .mapNotNull { field -> field.readStaticValue() }
            .firstOrNull { candidate ->
                candidate.javaClass.methods.any { method ->
                    method.parameterCount == 0 &&
                        method.returnType == managerClass &&
                        (method.name in preferredAccessorNames || Modifier.isStatic(method.modifiers))
                }
            }
    if (companion != null) {
        val accessor =
            companion.javaClass.methods
                .filter { method ->
                    method.parameterCount == 0 &&
                        method.returnType == managerClass &&
                        (method.name in preferredAccessorNames || Modifier.isStatic(method.modifiers))
                }.minWithOrNull(
                    compareBy<Method> { method ->
                        when (method.name) {
                            "j" -> 0
                            "G0" -> 1
                            "I" -> 2
                            else -> 3
                        }
                    }.thenBy { it.name },
                )
                ?.apply { isAccessible = true }
                ?: error("ChatRoomManager companion accessor not found")
        return { accessor.invoke(companion) ?: error("ChatRoomManager companion accessor returned null") }
    }
    val staticAccessor =
        managerClass.methods
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == managerClass
            }.minWithOrNull(
                compareBy<Method> { method ->
                    when (method.name) {
                        "G0" -> 0
                        "I" -> 1
                        else -> 2
                    }
                }.thenBy { it.name },
            )?.apply { isAccessible = true } ?: error("ChatRoomManager singleton accessor not found")
    return { staticAccessor.invoke(null) ?: error("ChatRoomManager static accessor returned null") }
}

internal fun resolveChatRoomEntityConversionMethod(
    registry: KakaoClassRegistry,
    owner: Class<*>,
    entityClass: Class<*>,
): Method? {
    val preferredNames = listOf("c", "b")
    return owner.methods
        .filter { method ->
            method.parameterCount == 1 &&
                registry.chatRoomClass.isAssignableFrom(method.returnType) &&
                method.parameterTypes[0].isAssignableFrom(entityClass)
        }.minWithOrNull(
            compareBy<Method> { method ->
                val index = preferredNames.indexOf(method.name)
                if (index >= 0) index else preferredNames.size
            }.thenBy { method -> method.name },
        )
}
