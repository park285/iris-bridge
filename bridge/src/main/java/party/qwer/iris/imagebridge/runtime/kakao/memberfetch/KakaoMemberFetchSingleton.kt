package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import java.lang.reflect.Modifier

internal fun resolveMemberFetchSingleton(clazz: Class<*>): Any? {
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type == clazz }
        .forEach { field ->
            runCatching {
                field.isAccessible = true
                return field.get(null)
            }
        }
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type != clazz }
        .forEach { holder ->
            runCatching {
                holder.isAccessible = true
                val holderValue = holder.get(null) ?: return@forEach
                holder.type.methods
                    .filter { method -> method.parameterCount == 0 && method.returnType == clazz }
                    .forEach { accessor ->
                        accessor.isAccessible = true
                        accessor.invoke(holderValue)?.let { return it }
                    }
            }
        }
    return null
}
