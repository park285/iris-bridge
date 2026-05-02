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
    ): RankedContainerCandidate? {
        val views = candidateCollector.views(container).filter { it.values.isNotEmpty() }
        if (views.isEmpty()) {
            return null
        }
        val userPath = fieldSelector.selectUserIdPath(views, expectedMemberIds) ?: return null
        val nicknamePath = fieldSelector.selectNicknamePath(views, userPath.path, expectedNicknames) ?: return null
        val profilePath = fieldSelector.selectProfileImagePath(views)
        val rolePath = fieldSelector.selectRolePath(views)
        val members =
            buildMembers(
                views = views,
                userPath = userPath.path,
                nicknamePath = nicknamePath.path,
                rolePath = rolePath?.path,
                profilePath = profilePath?.path,
            ).filter { member -> member.userId in expectedMemberIds }
        if (members.isEmpty()) {
            return null
        }

        val matchedExpected =
            expectedMemberIds.count { expectedId ->
                members.any { member -> member.userId == expectedId }
            }
        val expectedNicknameMatches =
            members.count { member ->
                expectedNicknames[member.userId] == member.nickname
            }
        val classBonus =
            views
                .groupingBy { it.className }
                .eachCount()
                .values
                .maxOrNull()
                ?: 0
        val plan =
            ExtractionPlan(
                containerPath = container.path,
                sourceClassName = views.firstOrNull { it.className != "<null>" }?.className,
                userIdPath = userPath.path,
                nicknamePath = nicknamePath.path,
                rolePath = rolePath?.path,
                profileImagePath = profilePath?.path,
            )
        return RankedContainerCandidate(
            plan = plan,
            members = members,
            score =
                members.size * 1_000 +
                    matchedExpected * 400 +
                    userPath.score +
                    nicknamePath.score +
                    (rolePath?.score ?: 0) +
                    (profilePath?.score ?: 0) +
                    containerPathScore(container.path) +
                    candidateCollector.typeScore(container) +
                    classBonus * 20,
            expectedNicknameMatches = expectedNicknameMatches,
            matchedExpectedCount = matchedExpected,
            hasRolePath = rolePath != null,
            hasProfilePath = profilePath != null,
            sourceClassName = plan.sourceClassName,
            containerType = candidateCollector.typeLabel(container),
            genericLabelPenalty = nicknamePath.genericPenalty,
        )
    }

    fun buildMembers(
        views: List<ElementView>,
        userPath: String,
        nicknamePath: String,
        rolePath: String?,
        profilePath: String?,
    ): List<ImageBridgeProtocol.ChatRoomMemberSnapshot> {
        val deduped = linkedMapOf<Long, ImageBridgeProtocol.ChatRoomMemberSnapshot>()
        views.forEach { view ->
            val userId = fieldSelector.primitiveLongValue(view.values[userPath])?.takeIf { it > 0L } ?: return@forEach
            val nickname =
                (view.values[nicknamePath] as? PrimitiveValue.StringValue)
                    ?.value
                    ?.trim()
                    ?.takeIf(fieldSelector::looksLikeNickname)
                    ?: return@forEach
            val candidate =
                ImageBridgeProtocol.ChatRoomMemberSnapshot(
                    userId = userId,
                    nickname = nickname,
                    roleCode = rolePath?.let { path -> fieldSelector.parseRoleCode(view.values[path]) },
                    profileImageUrl =
                        profilePath
                            ?.let { path -> (view.values[path] as? PrimitiveValue.StringValue)?.value?.trim() }
                            ?.takeIf(fieldSelector::looksLikeProfileUrl),
                )
            val current = deduped[userId]
            if (current == null || completeness(candidate) > completeness(current)) {
                deduped[userId] = candidate
            }
        }
        return deduped.values.toList()
    }

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

            else -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.LOW
        }

    private fun completeness(
        member: ImageBridgeProtocol.ChatRoomMemberSnapshot,
    ): Int = listOfNotNull(member.roleCode, member.profileImageUrl?.takeIf(String::isNotBlank)).size

    private fun containerPathScore(path: String): Int =
        fieldSelector.pathHintScore(
            path = path,
            preferredTokens = setOf("member", "members", "user", "users", "participant", "participants"),
            discouragedTokens = setOf("notice", "message", "thread", "backup"),
        )
}
