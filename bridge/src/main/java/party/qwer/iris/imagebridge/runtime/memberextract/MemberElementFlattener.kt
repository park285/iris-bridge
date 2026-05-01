package party.qwer.iris.imagebridge.runtime.memberextract

import java.util.Collections
import java.util.IdentityHashMap

internal class MemberElementFlattener(
    private val reflectionWalker: MemberReflectionWalker,
) {
    fun flattenMapEntry(entry: Map.Entry<*, *>): Map<String, PrimitiveValue> =
        linkedMapOf<String, PrimitiveValue>().apply {
            addSimpleValue(this, path = "key", value = entry.key)
            val value = entry.value
            if (value == null || value.let(reflectionWalker::isSimpleValue)) {
                addSimpleValue(this, path = "value", value = value)
            } else {
                flattenObject(
                    value = value,
                    prefix = "value",
                    depth = 0,
                    output = this,
                    visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()),
                )
            }
        }

    fun flattenElement(element: Any): Map<String, PrimitiveValue> =
        linkedMapOf<String, PrimitiveValue>().apply {
            flattenObject(
                value = element,
                prefix = null,
                depth = 0,
                output = this,
                visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()),
            )
        }

    private fun flattenObject(
        value: Any?,
        prefix: String?,
        depth: Int,
        output: MutableMap<String, PrimitiveValue>,
        visited: MutableSet<Any>,
    ) {
        if (value == null || depth > MAX_MEMBER_FIELD_DEPTH) {
            return
        }
        if (reflectionWalker.isSimpleValue(value)) {
            addSimpleValue(output, prefix ?: "value", value)
            return
        }
        if (!reflectionWalker.shouldDescendInto(value.javaClass) || !visited.add(value)) {
            return
        }
        reflectionWalker.instanceFields(value.javaClass).forEach { field ->
            runCatching {
                val child = field.get(value)
                val childPath = prefix?.let { "$it.${field.name}" } ?: field.name
                if (child == null || child.let(reflectionWalker::isSimpleValue)) {
                    addSimpleValue(output, childPath, child)
                } else if (!child.javaClass.isArray && child !is Collection<*> && child !is Map<*, *>) {
                    flattenObject(child, childPath, depth + 1, output, visited)
                }
            }
        }
    }

    private fun addSimpleValue(
        output: MutableMap<String, PrimitiveValue>,
        path: String,
        value: Any?,
    ) {
        val primitive =
            when (value) {
                is Byte -> PrimitiveValue.LongValue(value.toLong())
                is Short -> PrimitiveValue.LongValue(value.toLong())
                is Int -> PrimitiveValue.LongValue(value.toLong())
                is Long -> PrimitiveValue.LongValue(value)
                is String -> PrimitiveValue.StringValue(value)
                is Enum<*> -> PrimitiveValue.StringValue(value.name)
                else -> null
            } ?: return
        output[path] = primitive
    }
}
