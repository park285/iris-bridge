package party.qwer.iris.imagebridge.runtime.room

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal fun Field.readStaticValue(): Any? =
    runCatching {
        isAccessible = true
        get(null)
    }.getOrNull()

internal fun interface RoomEntityResolver {
    fun resolve(entity: Any): Any?
}

internal class CompanionRoomEntityResolver(
    private val companion: Any,
    private val method: Method,
) : RoomEntityResolver {
    override fun resolve(entity: Any): Any? = method.invoke(companion, entity)
}

internal class ConstructorRoomEntityResolver(
    private val constructor: Constructor<*>,
) : RoomEntityResolver {
    override fun resolve(entity: Any): Any? = constructor.newInstance(entity)
}
