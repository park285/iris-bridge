package party.qwer.iris.imagebridge.runtime.room

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal class ChatRoomResolver(
    private val registry: KakaoClassRegistry,
) {
    companion object {
        private const val TAG = "IrisBridge"
    }

    private val roomEntityResolverCache = ConcurrentHashMap<Class<*>, RoomEntityResolver>()
    private val managerAccessor by lazy { resolveManagerAccessor() }

    fun resolve(roomId: Long): Any? {
        runCatching { resolveFromDatabase(roomId) }
            .onFailure { Log.e(TAG, "primary room resolver failed: ${it.message}", it) }
            .getOrNull()
            ?.let { return it }

        runCatching { resolveFromManager(roomId) }
            .onFailure { Log.e(TAG, "manager resolver failed: ${it.message}", it) }
            .getOrNull()
            ?.let { return it }

        return null
    }

    private fun resolveFromDatabase(roomId: Long): Any? {
        val database =
            registry.masterDbSingletonField.apply { isAccessible = true }.get(null)
                ?: error("MasterDatabase not initialized")
        val roomDao = registry.roomDaoMethod.apply { isAccessible = true }.invoke(database)
        val entity = registry.entityLookupMethod.apply { isAccessible = true }.invoke(roomDao, roomId) ?: return null
        val resolver =
            roomEntityResolverCache.computeIfAbsent(entity.javaClass) { entityClass ->
                resolveRoomEntityResolver(entityClass)
            }
        return resolver.resolve(entity)
    }

    private fun resolveFromManager(roomId: Long): Any? {
        val manager = managerAccessor()
        val broadResult = registry.broadRoomResolverMethod.apply { isAccessible = true }.invoke(manager, roomId, true, true)
        if (broadResult != null) return broadResult
        return registry.directRoomResolverMethod.apply { isAccessible = true }.invoke(manager, roomId)
    }

    private fun resolveRoomEntityResolver(entityClass: Class<*>): RoomEntityResolver {
        registry.chatRoomClass.declaredFields
            .asSequence()
            .filter { Modifier.isStatic(it.modifiers) }
            .mapNotNull { field -> field.readStaticValue() }
            .firstOrNull { candidate ->
                candidate.javaClass.methods.any { method ->
                    method.name == "c" &&
                        method.parameterCount == 1 &&
                        registry.chatRoomClass.isAssignableFrom(method.returnType) &&
                        method.parameterTypes[0].isAssignableFrom(entityClass)
                }
            }?.let { companion ->
                val resolverMethod =
                    companion.javaClass.methods
                        .first { method ->
                            method.name == "c" &&
                                method.parameterCount == 1 &&
                                registry.chatRoomClass.isAssignableFrom(method.returnType) &&
                                method.parameterTypes[0].isAssignableFrom(entityClass)
                        }.apply { isAccessible = true }
                return CompanionRoomEntityResolver(companion, resolverMethod)
            }

        val constructor =
            registry.chatRoomClass.declaredConstructors.firstOrNull { candidate ->
                candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0].isAssignableFrom(entityClass)
            } ?: error("ChatRoom companion/constructor resolver not found for ${entityClass.name}")
        constructor.isAccessible = true
        return ConstructorRoomEntityResolver(constructor)
    }

    private fun resolveManagerAccessor(): () -> Any {
        val managerClass = registry.chatRoomManagerClass
        val companion =
            managerClass.declaredFields
                .asSequence()
                .filter { Modifier.isStatic(it.modifiers) }
                .mapNotNull { field -> field.readStaticValue() }
                .firstOrNull { candidate ->
                    candidate.javaClass.methods.any { method ->
                        method.name == "j" &&
                            method.parameterCount == 0 &&
                            method.returnType == managerClass
                    }
                }
        if (companion != null) {
            val accessor =
                companion.javaClass.methods
                    .first { method ->
                        method.name == "j" &&
                            method.parameterCount == 0 &&
                            method.returnType == managerClass
                    }.apply { isAccessible = true }
            return { accessor.invoke(companion) ?: error("ChatRoomManager companion accessor returned null") }
        }
        val staticAccessor =
            managerClass.methods
                .firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType == managerClass
                }?.apply { isAccessible = true } ?: error("ChatRoomManager singleton accessor not found")
        return { staticAccessor.invoke(null) ?: error("ChatRoomManager static accessor returned null") }
    }
}
