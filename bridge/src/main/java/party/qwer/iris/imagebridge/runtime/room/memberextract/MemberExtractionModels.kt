package party.qwer.iris.imagebridge.runtime.room.memberextract

import party.qwer.iris.ImageBridgeProtocol

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

internal data class ExtractionPlan(
    val containerPath: String,
    val sourceClassName: String?,
    val userIdPath: String,
    val nicknamePath: String,
    val rolePath: String? = null,
    val profileImagePath: String? = null,
    val mentionUserIdPath: String? = null,
) {
    fun fingerprint(): String =
        listOfNotNull(
            containerPath,
            sourceClassName,
            userIdPath,
            nicknamePath,
            rolePath,
            profileImagePath,
            mentionUserIdPath,
        ).joinToString("|")
}

internal data class ElementView(
    val className: String,
    val values: Map<String, PrimitiveValue>,
)

internal data class CandidateStringValue(
    val path: String,
    val value: String,
    val userId: Long?,
)

internal data class ScoredPath(
    val path: String,
    val score: Int,
    val genericPenalty: Int = 0,
)

internal data class RankedContainerCandidate(
    val plan: ExtractionPlan,
    val members: List<ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    val score: Int,
    val expectedNicknameMatches: Int,
    val matchedExpectedCount: Int,
    val hasRolePath: Boolean,
    val hasProfilePath: Boolean,
    val sourceClassName: String?,
    val containerType: String,
    val genericLabelPenalty: Int,
)

internal const val MAX_GRAPH_DEPTH = 4
internal const val MAX_CONTAINER_ELEMENTS = 80
internal const val MAX_MEMBER_FIELD_DEPTH = 4
internal const val MAX_NICKNAME_LENGTH = 80
internal const val MAX_MENTION_USER_ID_LENGTH = 128
internal const val CONTAINER_TYPE_COLLECTION = "collection"
internal const val CONTAINER_TYPE_MAP = "map"
internal const val MAX_DEBUG_CONTAINER_SUMMARY = 8
internal const val MAX_DEBUG_VIEW_COUNT = 2
internal const val MAX_DEBUG_PATHS = 10
internal const val MAX_DEBUG_SAMPLE_COUNT = 12
internal const val MAX_DEBUG_VALUE_LENGTH = 48
internal const val MAX_DEBUG_MEMBER_IDS = 8
internal const val MAX_DEBUG_NICKNAMES = 8
internal const val TAG = "IrisBridge"
internal val KNOWN_ROLE_CODES = setOf(0, 1, 2, 4, 8)
internal val GENERIC_LABEL_VALUES = setOf("notice", "system", "default", "unknown", "profile", "member")
internal val INTERNAL_ARTIFACT_TOKENS =
    setOf(
        "backup",
        "openlink",
        "chatmember",
        "memberid",
        "userid",
        "nickname",
        "profile",
    )
