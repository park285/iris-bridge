package party.qwer.iris.imagebridge.runtime.send

import java.lang.reflect.Constructor
import java.lang.reflect.Method

internal data class KakaoBuilderConstructor(
    val constructor: Constructor<*>,
    val roomSource: BuilderRoomSource,
)

internal enum class BuilderRoomSource {
    CHAT_ROOM,
    ROOM_ID,
}

internal fun selectBuilderConstructor(
    builderClass: Class<*>,
    messageTypeClass: Class<*>,
    chatRoomClass: Class<*>,
): KakaoBuilderConstructor =
    builderClass.declaredConstructors
        .mapNotNull { constructor ->
            val types = constructor.parameterTypes
            when {
                types.size >= 4 &&
                    types[0].isAssignableFrom(chatRoomClass) &&
                    types[1].isAssignableFrom(messageTypeClass) ->
                    KakaoBuilderConstructor(constructor, BuilderRoomSource.CHAT_ROOM)
                types.size >= 5 &&
                    types[0] == Long::class.javaPrimitiveType &&
                    types[1].isAssignableFrom(messageTypeClass) ->
                    KakaoBuilderConstructor(constructor, BuilderRoomSource.ROOM_ID)
                else -> null
            }
        }.minWithOrNull(
            compareBy<KakaoBuilderConstructor> {
                if (it.roomSource == BuilderRoomSource.CHAT_ROOM) 0 else 1
            }.thenBy { it.constructor.parameterCount },
        )
        ?: error("ChatSendingLog builder constructor not found")

internal fun newBuilder(
    binding: KakaoBuilderConstructor,
    roomId: Long,
    chatRoom: Any,
    messageType: Any,
): Any {
    val constructor = binding.constructor
    val args =
        constructor.parameterTypes
            .mapIndexed { index, type ->
                when (index) {
                    0 ->
                        when (binding.roomSource) {
                            BuilderRoomSource.CHAT_ROOM -> chatRoom
                            BuilderRoomSource.ROOM_ID -> roomId
                        }
                    1 -> messageType
                    2 -> 0
                    3 -> null
                    else -> defaultBuilderArgument(index, type, binding.roomSource)
                }.coerceFor(type)
            }.toTypedArray()
    return constructor.apply { isAccessible = true }.newInstance(*args)
}

private fun defaultBuilderArgument(
    index: Int,
    type: Class<*>,
    roomSource: BuilderRoomSource,
): Any? =
    when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType && index > 3 ->
            when (roomSource) {
                BuilderRoomSource.CHAT_ROOM -> 12
                BuilderRoomSource.ROOM_ID -> 28
            }
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        else -> null
    }

private fun Any?.coerceFor(type: Class<*>): Any? =
    when {
        this != null -> this
        type.isPrimitive && type == Boolean::class.javaPrimitiveType -> false
        type.isPrimitive && type == Int::class.javaPrimitiveType -> 0
        type.isPrimitive && type == Long::class.javaPrimitiveType -> 0L
        else -> null
    }

internal fun selectBuildMethod(
    builderClass: Class<*>,
    sendingLogClass: Class<*>,
): Method =
    builderClass.methods
        .filter { method -> method.parameterCount == 0 && sendingLogClass.isAssignableFrom(method.returnType) }
        .minWithOrNull(compareBy<Method> { if (it.name == "b") 0 else 1 }.thenBy { it.name })
        ?: error("ChatSendingLog builder build method not found")

internal fun selectBuilderMethod(
    builderClass: Class<*>,
    vararg parameterTypes: Class<*>,
    preferredNames: Set<String>,
): Method? =
    builderClass.methods
        .filter { method ->
            method.parameterTypes.toList() == parameterTypes.toList() &&
                builderClass.isAssignableFrom(method.returnType)
        }.minWithOrNull(compareBy<Method> { if (it.name in preferredNames) 0 else 1 }.thenBy { it.name })
