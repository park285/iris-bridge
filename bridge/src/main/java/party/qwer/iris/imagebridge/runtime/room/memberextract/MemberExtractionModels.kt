package party.qwer.iris.imagebridge.runtime.room.memberextract

internal sealed interface ContainerCandidate {
    val path: String

    data class CollectionContainer(
        override val path: String,
        val elements: List<Any>,
    ) : ContainerCandidate

    data class MapContainer(
        override val path: String,
        val entries: List<Map.Entry<*, *>>,
    ) : ContainerCandidate
}

internal sealed interface PrimitiveValue {
    data class LongValue(
        val value: Long,
    ) : PrimitiveValue

    data class StringValue(
        val value: String,
    ) : PrimitiveValue
}

internal data class ElementView(
    val className: String,
    val values: Map<String, PrimitiveValue>,
)

internal const val MAX_GRAPH_DEPTH = 4
internal const val MAX_CONTAINER_ELEMENTS = 80
internal const val MAX_MEMBER_FIELD_DEPTH = 4
internal const val CONTAINER_TYPE_COLLECTION = "collection"
internal const val CONTAINER_TYPE_MAP = "map"
internal const val MAX_DEBUG_CONTAINER_SUMMARY = 8
internal const val MAX_DEBUG_VIEW_COUNT = 2
internal const val MAX_DEBUG_PATHS = 10
internal const val MAX_DEBUG_SAMPLE_COUNT = 12
internal const val TAG = "IrisBridge"
