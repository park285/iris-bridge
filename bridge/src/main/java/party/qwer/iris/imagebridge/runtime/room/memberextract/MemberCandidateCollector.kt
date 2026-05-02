package party.qwer.iris.imagebridge.runtime.room.memberextract

import java.util.Collections
import java.util.IdentityHashMap
import java.lang.reflect.Array as ReflectArray

internal class MemberCandidateCollector(
    private val reflectionWalker: MemberReflectionWalker,
    private val flattener: MemberElementFlattener,
) {
    fun collectContainers(room: Any): List<ContainerCandidate> {
        val containers = mutableListOf<ContainerCandidate>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

        fun visit(
            path: String,
            value: Any?,
            depth: Int,
        ) {
            if (value == null || depth > MAX_GRAPH_DEPTH || reflectionWalker.isSimpleValue(value)) {
                return
            }
            if (!visited.add(value)) {
                return
            }
            when {
                value is Collection<*> -> {
                    val elements = value.filterNotNull().take(MAX_CONTAINER_ELEMENTS)
                    if (elements.isNotEmpty()) {
                        containers += ContainerCandidate.CollectionContainer(path = path, elements = elements)
                    }
                    elements.forEachIndexed { index, element ->
                        visit("$path[$index]", element, depth + 1)
                    }
                }

                value is Map<*, *> -> {
                    val entries = value.entries.filter { it.key != null || it.value != null }.take(MAX_CONTAINER_ELEMENTS)
                    if (entries.isNotEmpty()) {
                        containers += ContainerCandidate.MapContainer(path = path, entries = entries)
                    }
                    entries.forEachIndexed { index, entry ->
                        visit("$path{$index}", entry.value, depth + 1)
                    }
                }

                value.javaClass.isArray -> {
                    val size = minOf(ReflectArray.getLength(value), MAX_CONTAINER_ELEMENTS)
                    val elements =
                        buildList(size) {
                            repeat(size) { index ->
                                ReflectArray.get(value, index)?.let(::add)
                            }
                        }
                    if (elements.isNotEmpty()) {
                        containers += ContainerCandidate.CollectionContainer(path = path, elements = elements)
                    }
                    elements.forEachIndexed { index, element ->
                        visit("$path[$index]", element, depth + 1)
                    }
                }

                reflectionWalker.shouldDescendInto(value.javaClass) -> {
                    reflectionWalker.instanceFields(value.javaClass).forEach { field ->
                        runCatching {
                            visit("$path.${field.name}", field.get(value), depth + 1)
                        }
                    }
                }
            }
        }

        visit("$", room, 0)
        return containers
    }

    fun views(container: ContainerCandidate): List<ElementView> =
        when (container) {
            is ContainerCandidate.CollectionContainer ->
                container.elements.map { element ->
                    ElementView(
                        className = element.javaClass.name,
                        values = flattener.flattenElement(element),
                    )
                }

            is ContainerCandidate.MapContainer ->
                container.entries.map { entry ->
                    ElementView(
                        className = entry.value?.javaClass?.name ?: entry.key?.javaClass?.name ?: "<null>",
                        values = flattener.flattenMapEntry(entry),
                    )
                }
        }

    fun typeScore(container: ContainerCandidate): Int =
        when (container) {
            is ContainerCandidate.CollectionContainer -> 120
            is ContainerCandidate.MapContainer -> -120
        }

    fun typeLabel(container: ContainerCandidate): String =
        when (container) {
            is ContainerCandidate.CollectionContainer -> CONTAINER_TYPE_COLLECTION
            is ContainerCandidate.MapContainer -> CONTAINER_TYPE_MAP
        }
}
