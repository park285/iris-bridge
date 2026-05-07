package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.room.memberextract.ContainerCandidate
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberContainerScorer
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberExtractionDiagnostics
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberExtractionPlanMapper
import party.qwer.iris.imagebridge.runtime.room.memberextract.RankedContainerCandidate

internal class ChatRoomMemberSnapshotBuilder(
    private val clock: () -> Long,
    private val scorer: MemberContainerScorer,
    private val planMapper: MemberExtractionPlanMapper,
    private val diagnostics: MemberExtractionDiagnostics,
) {
    fun discoverBestSnapshot(
        roomId: Long,
        containers: List<ContainerCandidate>,
        expected: ExpectedMemberHints,
        fallbackSnapshot: ImageBridgeProtocol.ChatRoomMembersSnapshot? = null,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val ranked =
            containers
                .mapNotNull { scorer.evaluateContainer(it, expected.ids, expected.nicknames, expected.mentionUserIds) }
                .sortedByDescending { it.score }
        val best =
            ranked.firstOrNull()
                ?: fallbackSnapshot
                    ?.let { return it }
                ?: run {
                    diagnostics.logMissingCandidateDiagnostics(roomId, containers, expected.ids, expected.nicknames)
                    error("chatroom member candidates not found")
                }
        val second = ranked.getOrNull(1)
        val candidateGap = second?.let { candidate -> best.score - candidate.score }
        return membersSnapshot(
            roomId = roomId,
            candidate = best,
            confidence = scorer.confidenceFor(best, candidateGap),
            confidenceScore = best.score,
            usedPreferredPlan = false,
            candidateGap = candidateGap,
        )
    }

    fun membersSnapshot(
        roomId: Long,
        candidate: RankedContainerCandidate,
        confidence: ImageBridgeProtocol.ChatRoomSnapshotConfidence,
        confidenceScore: Int,
        usedPreferredPlan: Boolean,
        candidateGap: Int?,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot =
        ImageBridgeProtocol.ChatRoomMembersSnapshot(
            roomId = roomId,
            sourcePath = candidate.plan.containerPath,
            sourceClassName = candidate.sourceClassName,
            scannedAtEpochMs = clock(),
            members = candidate.members.sortedBy { it.userId },
            selectedPlan = planMapper.toProtocolPlan(candidate.plan),
            confidence = confidence,
            confidenceScore = confidenceScore,
            usedPreferredPlan = usedPreferredPlan,
            candidateGap = candidateGap,
        )
}
