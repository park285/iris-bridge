package party.qwer.iris.imagebridge.runtime.room

import java.lang.reflect.Array
import java.lang.reflect.Modifier

internal class ChatRoomFieldScanner(
    private val maxCollectionSampleSize: Int = 3,
) {
    fun scanFields(
        obj: Any,
        maxDepth: Int,
        currentDepth: Int,
    ): List<ChatRoomIntrospector.FieldInfo> =
        obj.javaClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }
            .mapNotNull { field ->
                field.isAccessible = true
                try {
                    describeField(field.name, field.type.name, field.type, field.get(obj), maxDepth, currentDepth)
                } catch (_: Exception) {
                    ChatRoomIntrospector.FieldInfo(name = field.name, type = field.type.name, value = "<inaccessible>")
                }
            }

    private fun describeField(
        name: String,
        typeName: String,
        fieldType: Class<*>,
        value: Any?,
        maxDepth: Int,
        currentDepth: Int,
    ): ChatRoomIntrospector.FieldInfo =
        when {
            fieldType.isPrimitive || fieldType == String::class.java ->
                ChatRoomIntrospector.FieldInfo(name = name, type = typeName, value = value?.toString())
            value is Collection<*> ->
                ChatRoomIntrospector.FieldInfo(
                    name = name,
                    type = typeName,
                    size = value.size,
                    elementType = value.firstOrNull()?.javaClass?.name,
                    elements = sampleCollectionElements(value, maxDepth, currentDepth),
                )
            value != null && value.javaClass.isArray ->
                ChatRoomIntrospector.FieldInfo(
                    name = name,
                    type = typeName,
                    size = Array.getLength(value),
                    elementType = value.javaClass.componentType?.name,
                    elements = sampleArrayElements(value, maxDepth, currentDepth),
                )
            value is Map<*, *> -> ChatRoomIntrospector.FieldInfo(name = name, type = typeName, size = value.size)
            value != null && currentDepth < maxDepth ->
                ChatRoomIntrospector.FieldInfo(
                    name = name,
                    type = typeName,
                    nested = scanFields(value, maxDepth, currentDepth + 1),
                )
            else -> ChatRoomIntrospector.FieldInfo(name = name, type = typeName, value = value?.toString())
        }

    private fun sampleCollectionElements(
        values: Collection<*>,
        maxDepth: Int,
        currentDepth: Int,
    ): List<ChatRoomIntrospector.FieldInfo> =
        values.take(maxCollectionSampleSize).mapIndexed { index, value ->
            describeElement(index, value, maxDepth, currentDepth)
        }

    private fun sampleArrayElements(
        array: Any,
        maxDepth: Int,
        currentDepth: Int,
    ): List<ChatRoomIntrospector.FieldInfo> =
        buildList {
            val size = minOf(Array.getLength(array), maxCollectionSampleSize)
            repeat(size) { index ->
                add(describeElement(index, Array.get(array, index), maxDepth, currentDepth))
            }
        }

    private fun describeElement(
        index: Int,
        value: Any?,
        maxDepth: Int,
        currentDepth: Int,
    ): ChatRoomIntrospector.FieldInfo {
        val typeName = value?.javaClass?.name ?: "null"
        return when {
            value == null || value.javaClass.isPrimitive || value is String || value is Number || value is Boolean || value is Char ->
                ChatRoomIntrospector.FieldInfo(name = "[$index]", type = typeName, value = value?.toString())
            currentDepth < maxDepth ->
                ChatRoomIntrospector.FieldInfo(
                    name = "[$index]",
                    type = typeName,
                    nested = scanFields(value, maxDepth, currentDepth + 1),
                )
            else -> ChatRoomIntrospector.FieldInfo(name = "[$index]", type = typeName, value = value.toString())
        }
    }
}
