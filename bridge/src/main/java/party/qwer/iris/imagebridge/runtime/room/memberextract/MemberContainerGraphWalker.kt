package party.qwer.iris.imagebridge.runtime.room.memberextract

import java.util.Collections
import java.util.IdentityHashMap
import java.lang.reflect.Array as ReflectArray

internal class MemberContainerGraphWalker(
    private val reflectionWalker: MemberReflectionWalker,
) {
    private val containers = mutableListOf<ContainerCandidate>()
    private val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    fun collect(room: Any): List<ContainerCandidate> {
        visit("$", room, 0)
        return containers
    }

    private fun visit(
        path: String,
        value: Any?,
        depth: Int,
    ) {
        if (value == null || depth > MAX_GRAPH_DEPTH || reflectionWalker.isSimpleValue(value)) return
        if (!visited.add(value)) return
        when {
            value is Collection<*> -> visitCollection(path, value, depth)
            value is Map<*, *> -> visitMap(path, value, depth)
            value.javaClass.isArray -> visitArray(path, value, depth)
            reflectionWalker.shouldDescendInto(value.javaClass) -> visitObjectFields(path, value, depth)
        }
    }

    private fun visitCollection(
        path: String,
        value: Collection<*>,
        depth: Int,
    ) {
        val elements = value.filterNotNull().take(MAX_CONTAINER_ELEMENTS)
        if (elements.isNotEmpty()) {
            containers += ContainerCandidate.CollectionContainer(path = path, elements = elements)
        }
        elements.forEachIndexed { index, element -> visit("$path[$index]", element, depth + 1) }
    }

    private fun visitMap(
        path: String,
        value: Map<*, *>,
        depth: Int,
    ) {
        val entries = value.entries.filter { it.key != null || it.value != null }.take(MAX_CONTAINER_ELEMENTS)
        if (entries.isNotEmpty()) {
            containers += ContainerCandidate.MapContainer(path = path, entries = entries)
        }
        entries.forEachIndexed { index, entry -> visit("$path{$index}", entry.value, depth + 1) }
    }

    private fun visitArray(
        path: String,
        value: Any,
        depth: Int,
    ) {
        val size = minOf(ReflectArray.getLength(value), MAX_CONTAINER_ELEMENTS)
        val elements =
            buildList(size) {
                repeat(size) { index -> ReflectArray.get(value, index)?.let(::add) }
            }
        if (elements.isNotEmpty()) {
            containers += ContainerCandidate.CollectionContainer(path = path, elements = elements)
        }
        elements.forEachIndexed { index, element -> visit("$path[$index]", element, depth + 1) }
    }

    private fun visitObjectFields(
        path: String,
        value: Any,
        depth: Int,
    ) {
        reflectionWalker.instanceFields(value.javaClass).forEach { field ->
            runCatching { visit("$path.${field.name}", field.get(value), depth + 1) }
        }
    }
}
