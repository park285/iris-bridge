package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.room.memberextract.ContainerCandidate
import party.qwer.iris.imagebridge.runtime.room.memberextract.ExtractionPlan
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberCandidateCollector
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberContainerScorer
import party.qwer.iris.imagebridge.runtime.room.memberextract.RankedContainerCandidate

internal class PreferredMemberPlanApplier(
    private val candidateCollector: MemberCandidateCollector,
    private val scorer: MemberContainerScorer,
    private val snapshotBuilder: ChatRoomMemberSnapshotBuilder,
) {
    fun apply(
        roomId: Long,
        containers: List<ContainerCandidate>,
        expected: ExpectedMemberHints,
        preferredPlan: ExtractionPlan,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot? {
        val container = containers.firstOrNull { candidate -> candidate.path == preferredPlan.containerPath } ?: return null
        val views = candidateCollector.views(container).filter { it.values.isNotEmpty() }
        if (views.isEmpty()) return null
        val sourceClassName = views.firstOrNull { it.className != "<null>" }?.className
        if (!preferredPlan.sourceClassName.isNullOrBlank() && preferredPlan.sourceClassName != sourceClassName) return null
        val members =
            scorer
                .buildMembers(
                    views = views,
                    userPath = preferredPlan.userIdPath,
                    nicknamePath = preferredPlan.nicknamePath,
                    rolePath = preferredPlan.rolePath,
                    profilePath = preferredPlan.profileImagePath,
                    mentionUserIdPath = preferredPlan.mentionUserIdPath,
                    mentionUserIds = expected.mentionUserIds,
                ).filter { member -> member.userId in expected.ids }
        if (members.isEmpty()) return null
        val matchedExpected = expected.ids.count { expectedId -> members.any { member -> member.userId == expectedId } }
        if (matchedExpected == 0) return null
        val expectedNicknameMatches =
            members.count { member -> expected.nicknames[member.userId] == member.nickname }
        val score = 10_000 + matchedExpected * 400 + expectedNicknameMatches * 600
        return snapshotBuilder.membersSnapshot(
            roomId = roomId,
            candidate =
                RankedContainerCandidate(
                    plan = preferredPlan,
                    members = members,
                    score = score,
                    expectedNicknameMatches = expectedNicknameMatches,
                    matchedExpectedCount = matchedExpected,
                    hasRolePath = preferredPlan.rolePath != null,
                    hasProfilePath = preferredPlan.profileImagePath != null,
                    sourceClassName = sourceClassName,
                    containerType = candidateCollector.typeLabel(container),
                    genericLabelPenalty = 0,
                ),
            confidence = ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH,
            confidenceScore = score,
            usedPreferredPlan = true,
            candidateGap = null,
        )
    }
}
