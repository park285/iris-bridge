package party.qwer.iris.imagebridge.runtime.room

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Method
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

    fun resolveFresh(roomId: Long): Any? =
        runCatching { resolveFromManager(roomId) }
            .onFailure { Log.e(TAG, "fresh manager resolver failed: ${it.message}", it) }
            .getOrNull()
            ?: runCatching { resolveFromDatabase(roomId) }
                .onFailure { Log.e(TAG, "fresh database resolver failed: ${it.message}", it) }
                .getOrNull()

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
            .firstNotNullOfOrNull { candidate ->
                resolveEntityConversionMethod(candidate.javaClass, entityClass)?.let { resolverMethod ->
                    CompanionRoomEntityResolver(candidate, resolverMethod.apply { isAccessible = true })
                }
            }?.let { return it }

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

    private fun resolveEntityConversionMethod(
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
}
