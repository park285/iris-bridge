package party.qwer.iris.imagebridge.runtime.room.memberextract

internal class MemberCandidateCollector(
    private val reflectionWalker: MemberReflectionWalker,
    private val flattener: MemberElementFlattener,
) {
    fun collectContainers(room: Any): List<ContainerCandidate> = MemberContainerGraphWalker(reflectionWalker).collect(room)

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

    fun typeLabel(container: ContainerCandidate): String =
        when (container) {
            is ContainerCandidate.CollectionContainer -> CONTAINER_TYPE_COLLECTION
            is ContainerCandidate.MapContainer -> CONTAINER_TYPE_MAP
        }
}
