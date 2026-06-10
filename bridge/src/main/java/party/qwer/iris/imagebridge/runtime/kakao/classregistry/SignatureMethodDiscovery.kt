@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun selectMethodBySignature(
    label: String,
    candidates: List<Method>,
    preferredNames: Set<String> = emptySet(),
): Method = selectMethodCandidate(label, candidates, preferredNames)

internal fun resolveShareManagerSingleton(shareManagerClass: Class<*>): Any =
    shareManagerClass.declaredFields
        .asSequence()
        .filter { field -> Modifier.isStatic(field.modifiers) && shareManagerClass.isAssignableFrom(field.type) }
        .mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                field.get(null)
            }.getOrNull()
        }.firstOrNull()
        ?: error("ShareManager singleton not found")

internal fun discoverShareManagerClass(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    targetPackage: String,
): Class<*> =
    discoverClass(
        classLoader,
        scanner,
        lastKnownNames = arrayOf("$targetPackage.manager.ShareManager"),
        label = "ShareManager",
    ) { clazz ->
        clazz.declaredFields.any { field ->
            Modifier.isStatic(field.modifiers) && clazz.isAssignableFrom(field.type)
        } &&
            clazz.methods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == List::class.java &&
                    method.returnType.name == "android.content.Intent"
            }
    }

internal fun discoverSendListenerClass(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    shareManagerClass: Class<*>,
    chatRoomClass: Class<*>,
    targetPackage: String,
): Class<*> {
    val dispatchCandidates =
        shareManagerClass.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 4 &&
                method.parameterTypes[1].name == "android.content.Intent" &&
                method.parameterTypes[2].isAssignableFrom(chatRoomClass) &&
                method.parameterTypes[3] == Boolean::class.javaPrimitiveType
        }
    val listenerType =
        dispatchCandidates
            .map { method -> method.parameterTypes[0] }
            .distinct()
            .singleOrNull()
            ?: error("ShareManager dispatch listener type not found")
    if (listenerType.name.startsWith("$targetPackage.")) {
        return listenerType
    }
    return discoverClass(
        classLoader,
        scanner,
        lastKnownNames = arrayOf("$targetPackage.manager.send.m"),
        label = "ShareManager listener",
    ) { clazz -> clazz == listenerType || listenerType.isAssignableFrom(clazz) }
}
