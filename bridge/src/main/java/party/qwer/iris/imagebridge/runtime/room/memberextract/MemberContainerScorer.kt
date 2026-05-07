package party.qwer.iris.imagebridge.runtime.room.memberextract

import party.qwer.iris.ImageBridgeProtocol

internal class MemberContainerScorer(
    private val candidateCollector: MemberCandidateCollector,
    private val fieldSelector: MemberFieldSelector,
) {
    fun evaluateContainer(
        container: ContainerCandidate,
        expectedMemberIds: Set<Long>,
        expectedNicknames: Map<Long, String>,
        expectedMentionUserIds: Map<Long, String>,
    ): RankedContainerCandidate? =
        evaluateMemberContainer(
            container = container,
            expectedMemberIds = expectedMemberIds,
            expectedNicknames = expectedNicknames,
            expectedMentionUserIds = expectedMentionUserIds,
            candidateCollector = candidateCollector,
            fieldSelector = fieldSelector,
        )

    fun buildMembers(
        views: List<ElementView>,
        userPath: String,
        nicknamePath: String,
        rolePath: String?,
        profilePath: String?,
        mentionUserIdPath: String? = null,
        mentionUserIds: Map<Long, String> = emptyMap(),
    ): List<ImageBridgeProtocol.ChatRoomMemberSnapshot> =
        buildChatRoomMembers(
            views = views,
            userPath = userPath,
            nicknamePath = nicknamePath,
            rolePath = rolePath,
            profilePath = profilePath,
            mentionUserIdPath = mentionUserIdPath,
            mentionUserIds = mentionUserIds,
            fieldSelector = fieldSelector,
        )

    fun confidenceFor(
        candidate: RankedContainerCandidate,
        candidateGap: Int?,
    ): ImageBridgeProtocol.ChatRoomSnapshotConfidence =
        when {
            candidate.expectedNicknameMatches >= 2 && (candidateGap ?: 0) >= 180 -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH
            candidate.expectedNicknameMatches >= 1 &&
                candidate.containerType == CONTAINER_TYPE_COLLECTION &&
                candidate.genericLabelPenalty < 100 &&
                (candidateGap ?: 0) >= 160 -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH
            candidate.matchedExpectedCount >= 2 &&
                candidate.containerType == CONTAINER_TYPE_COLLECTION &&
                ((candidateGap ?: 0) >= 120 || candidate.hasRolePath || candidate.hasProfilePath) -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM
            candidate.matchedExpectedCount >= 1 &&
                candidate.containerType == CONTAINER_TYPE_COLLECTION &&
                candidate.hasRolePath &&
                candidate.hasProfilePath -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM
            candidate.matchedExpectedCount >= 2 &&
                candidate.containerType == CONTAINER_TYPE_MAP &&
                (candidate.expectedNicknameMatches >= 2 || candidate.members.size >= 2) -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM
            candidate.matchedExpectedCount == 0 &&
                candidate.containerType == CONTAINER_TYPE_COLLECTION &&
                candidate.members.size >= 2 &&
                candidate.genericLabelPenalty < 100 &&
                safeUnanchoredMemberContainerPath(candidate.plan.containerPath) -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM
            else -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.LOW
        }
}

private fun safeUnanchoredMemberContainerPath(path: String): Boolean {
    val normalized = path.lowercase()
    if (UNANCHORED_DISCOURAGED_CONTAINER_TOKENS.any { token -> normalized.contains(token) }) return false
    return UNANCHORED_MEMBER_CONTAINER_TOKENS.any { token -> normalized.contains(token) }
}
