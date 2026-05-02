package party.qwer.iris.imagebridge.runtime.room.memberextract

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal class MemberReflectionWalker {
    private val fieldCache = ConcurrentHashMap<Class<*>, List<Field>>()

    fun instanceFields(clazz: Class<*>): List<Field> =
        fieldCache.computeIfAbsent(clazz) { resolvedClass ->
            buildList {
                var current: Class<*>? = resolvedClass
                while (current != null && current != Any::class.java) {
                    current.declaredFields
                        .filterNot { field -> Modifier.isStatic(field.modifiers) }
                        .forEach { field ->
                            field.isAccessible = true
                            add(field)
                        }
                    current = current.superclass
                }
            }
        }

    fun shouldDescendInto(clazz: Class<*>): Boolean {
        val name = clazz.name
        return !clazz.isPrimitive &&
            !name.startsWith("java.") &&
            !name.startsWith("javax.") &&
            !name.startsWith("kotlin.") &&
            !name.startsWith("android.")
    }

    fun isSimpleValue(value: Any): Boolean =
        value is String ||
            value is Byte ||
            value is Short ||
            value is Int ||
            value is Long ||
            value is Enum<*>
}
