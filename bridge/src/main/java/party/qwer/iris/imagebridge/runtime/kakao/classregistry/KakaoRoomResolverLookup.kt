@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun resolveBroadRoomResolver(chatRoomManager: Class<*>) =
    selectMethodCandidate(
        label = "ChatRoomManager broad resolver on ${chatRoomManager.name}",
        candidates = chatRoomManager.declaredMethods.filter(::isBroadRoomResolverSignature),
        preferredNames = setOf("e0"),
    )

internal fun resolveDirectRoomResolver(
    chatRoomManager: Class<*>,
    broadResolver: Method,
    chatRoom: Class<*>,
) = selectMethodCandidate(
    label = "ChatRoomManager direct resolver on ${chatRoomManager.name}",
    candidates =
        chatRoomManager.declaredMethods.filter { method ->
            method != broadResolver &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType)) &&
                chatRoom.isAssignableFrom(method.returnType)
        },
    preferredNames = setOf("d0"),
)

internal fun resolveMasterDatabaseSingleton(masterDb: Class<*>) = selectMasterDatabaseSingletonField(masterDb).apply { isAccessible = true }

private fun selectMasterDatabaseSingletonField(masterDb: Class<*>): Field {
    val staticSelfFields =
        masterDb.declaredFields.filter { field ->
            Modifier.isStatic(field.modifiers) && field.type == masterDb
        }
    staticSelfFields.firstOrNull { field -> field.name == "q" }?.let { return it }
    return selectFieldCandidate(
        label = "MasterDatabase singleton field on ${masterDb.name}",
        candidates = staticSelfFields,
    )
}

internal fun resolveRoomDao(masterDb: Class<*>) =
    selectMethodCandidate(
        label = "MasterDatabase roomDao accessor on ${masterDb.name}",
        candidates =
            masterDb.methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType != Void.TYPE &&
                    method.returnType != masterDb &&
                    method.returnType != Any::class.java &&
                    method.returnType.methods.any { daoMethod ->
                        isRoomEntityLookupSignature(daoMethod, method.returnType)
                    }
            },
        preferredNames = setOf("O"),
    )

internal fun resolveEntityLookup(daoClass: Class<*>) =
    selectMethodCandidate(
        label = "RoomDao entity lookup on ${daoClass.name}",
        candidates = daoClass.methods.filter { method -> isRoomEntityLookupSignature(method, daoClass) },
        preferredNames = setOf("h", "k"),
    )

internal fun isBroadRoomResolverSignature(method: Method): Boolean =
    !Modifier.isStatic(method.modifiers) &&
        method.parameterTypes.contentEquals(
            arrayOf(
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ),
        )

private fun isRoomEntityLookupSignature(
    method: Method,
    daoClass: Class<*>,
): Boolean =
    !Modifier.isStatic(method.modifiers) &&
        method.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType)) &&
        !method.returnType.isPrimitive &&
        method.returnType != Void.TYPE &&
        method.returnType != daoClass &&
        method.returnType != Any::class.java &&
        method.returnType != java.lang.Integer::class.java &&
        method.returnType != java.lang.Long::class.java &&
        method.returnType != java.lang.Boolean::class.java &&
        method.returnType != String::class.java
