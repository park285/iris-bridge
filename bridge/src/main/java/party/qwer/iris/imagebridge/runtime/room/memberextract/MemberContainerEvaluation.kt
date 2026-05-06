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
            mentionUserIds = expectedMentionUserIds,
            fieldSelector = fieldSelector,
        ).filter { member -> member.userId in expectedMemberIds }
    if (members.isEmpty()) return null
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
        score = memberContainerScore(container, members, paths, matchedExpected, classBonus, candidateCollector, fieldSelector),
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
    candidateCollector: MemberCandidateCollector,
    fieldSelector: MemberFieldSelector,
): Int =
    members.size * 1_000 +
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
        classBonus * 20
