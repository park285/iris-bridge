package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import java.lang.reflect.Modifier

internal fun resolveMemberFetchSingleton(clazz: Class<*>): Any? {
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type == clazz }
        .sortedBy { field -> if (field.name == "b") 0 else 1 }
        .forEach { field ->
            runCatching {
                field.isAccessible = true
                field.get(null)?.let { return it }
            }
        }
    clazz.declaredMethods
        .filter { method -> Modifier.isStatic(method.modifiers) && method.parameterCount == 0 && method.returnType == clazz }
        .forEach { method ->
            runCatching {
                method.isAccessible = true
                method.invoke(null)?.let { return it }
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
