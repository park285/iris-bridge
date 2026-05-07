package party.qwer.iris.imagebridge.runtime.room.memberextract

private data class SelectedMemberPaths(
    val userPath: ScoredPath,
    val nicknamePath: ScoredPath,
    val rolePath: ScoredPath?,
    val profilePath: ScoredPath?,
    val mentionUserIdPath: ScoredPath?,
)

internal fun evaluateMemberContainer(
    container: ContainerCandidate,
    expectedMemberIds: Set<Long>,
    expectedNicknames: Map<Long, String>,
    expectedMentionUserIds: Map<Long, String>,
    candidateCollector: MemberCandidateCollector,
    fieldSelector: MemberFieldSelector,
): RankedContainerCandidate? {
    val views = candidateCollector.views(container).filter { it.values.isNotEmpty() }
    if (views.isEmpty()) return null
    val paths = selectMemberPaths(views, expectedMemberIds, expectedNicknames, fieldSelector) ?: return null
    val members =
        buildChatRoomMembers(
            views = views,
            userPath = paths.userPath.path,
            nicknamePath = paths.nicknamePath.path,
            rolePath = paths.rolePath?.path,
            profilePath = paths.profilePath?.path,
            mentionUserIdPath = paths.mentionUserIdPath?.path,
            expectedNicknames = expectedNicknames,
            mentionUserIds = expectedMentionUserIds,
            fieldSelector = fieldSelector,
        ).filter { member -> expectedMemberIds.isEmpty() || member.userId in expectedMemberIds }
    if (members.isEmpty()) return null
    if (expectedMemberIds.isEmpty() && !isSafeUnanchoredMemberContainer(container, members, paths, candidateCollector)) return null
    return rankMemberContainer(container, views, members, paths, expectedMemberIds, expectedNicknames, candidateCollector, fieldSelector)
}

private fun selectMemberPaths(
    views: List<ElementView>,
    expectedMemberIds: Set<Long>,
    expectedNicknames: Map<Long, String>,
    fieldSelector: MemberFieldSelector,
): SelectedMemberPaths? {
    val userPath = fieldSelector.selectUserIdPath(views, expectedMemberIds) ?: return null
    val nicknamePath = fieldSelector.selectNicknamePath(views, userPath.path, expectedNicknames) ?: return null
    return SelectedMemberPaths(
        userPath = userPath,
        nicknamePath = nicknamePath,
        rolePath = fieldSelector.selectRolePath(views),
        profilePath = fieldSelector.selectProfileImagePath(views),
        mentionUserIdPath = fieldSelector.selectMentionUserIdPath(views, userPath.path, nicknamePath.path),
    )
}

private fun isSafeUnanchoredMemberContainer(
    container: ContainerCandidate,
    members: List<party.qwer.iris.ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    paths: SelectedMemberPaths,
    candidateCollector: MemberCandidateCollector,
): Boolean {
    if (members.size < 2) return false
    if (candidateCollector.typeLabel(container) != CONTAINER_TYPE_COLLECTION) return false
    if (paths.nicknamePath.genericPenalty >= 100) return false
    val normalizedPath = container.path.lowercase()
    if (UNANCHORED_DISCOURAGED_CONTAINER_TOKENS.any { token -> normalizedPath.contains(token) }) return false
    return UNANCHORED_MEMBER_CONTAINER_TOKENS.any { token -> normalizedPath.contains(token) }
}

private fun rankMemberContainer(
    container: ContainerCandidate,
    views: List<ElementView>,
    members: List<party.qwer.iris.ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    paths: SelectedMemberPaths,
    expectedMemberIds: Set<Long>,
    expectedNicknames: Map<Long, String>,
    candidateCollector: MemberCandidateCollector,
    fieldSelector: MemberFieldSelector,
): RankedContainerCandidate {
    val matchedExpected = expectedMemberIds.count { expectedId -> members.any { member -> member.userId == expectedId } }
    val expectedNicknameMatches = members.count { member -> expectedNicknames[member.userId] == member.nickname }
    val classBonus =
        views
            .groupingBy { it.className }
            .eachCount()
            .values
            .maxOrNull() ?: 0
    val plan =
        ExtractionPlan(
            containerPath = container.path,
            sourceClassName = views.firstOrNull { it.className != "<null>" }?.className,
            userIdPath = paths.userPath.path,
            nicknamePath = paths.nicknamePath.path,
            rolePath = paths.rolePath?.path,
            profileImagePath = paths.profilePath?.path,
            mentionUserIdPath = paths.mentionUserIdPath?.path,
        )
    return RankedContainerCandidate(
        plan = plan,
        members = members,
        score =
            memberContainerScore(
                container = container,
                members = members,
                paths = paths,
                matchedExpected = matchedExpected,
                classBonus = classBonus,
                unanchored = expectedMemberIds.isEmpty(),
                candidateCollector = candidateCollector,
                fieldSelector = fieldSelector,
            ),
        expectedNicknameMatches = expectedNicknameMatches,
        matchedExpectedCount = matchedExpected,
        hasRolePath = paths.rolePath != null,
        hasProfilePath = paths.profilePath != null,
        sourceClassName = plan.sourceClassName,
        containerType = candidateCollector.typeLabel(container),
        genericLabelPenalty = paths.nicknamePath.genericPenalty,
    )
}

private fun memberContainerScore(
    container: ContainerCandidate,
    members: List<party.qwer.iris.ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    paths: SelectedMemberPaths,
    matchedExpected: Int,
    classBonus: Int,
    unanchored: Boolean,
    candidateCollector: MemberCandidateCollector,
    fieldSelector: MemberFieldSelector,
): Int =
    memberCountScore(members.size, unanchored) +
        matchedExpected * 400 +
        paths.userPath.score +
        paths.nicknamePath.score +
        (paths.rolePath?.score ?: 0) +
        (paths.profilePath?.score ?: 0) +
        (paths.mentionUserIdPath?.score ?: 0) +
        fieldSelector.pathHintScore(
            path = container.path,
            preferredTokens = setOf("member", "members", "user", "users", "participant", "participants"),
            discouragedTokens = setOf("notice", "message", "thread", "backup"),
        ) +
        candidateCollector.typeScore(container) +
        classBonusScore(classBonus, unanchored)

private fun memberCountScore(
    memberCount: Int,
    unanchored: Boolean,
): Int =
    if (unanchored) {
        memberCount.coerceAtMost(8) * 40
    } else {
        memberCount * 1_000
    }

private fun classBonusScore(
    classBonus: Int,
    unanchored: Boolean,
): Int =
    if (unanchored) {
        classBonus.coerceAtMost(3) * 20
    } else {
        classBonus * 20
    }

internal val UNANCHORED_MEMBER_CONTAINER_TOKENS = setOf("member", "members", "user", "users", "participant", "participants")
internal val UNANCHORED_DISCOURAGED_CONTAINER_TOKENS = setOf("notice", "message", "messages", "thread", "threads", "backup")
