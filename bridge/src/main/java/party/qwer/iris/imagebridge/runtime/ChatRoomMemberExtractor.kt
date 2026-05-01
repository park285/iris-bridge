package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.memberextract.ContainerCandidate
import party.qwer.iris.imagebridge.runtime.memberextract.ExtractionPlan
import party.qwer.iris.imagebridge.runtime.memberextract.MemberCandidateCollector
import party.qwer.iris.imagebridge.runtime.memberextract.MemberContainerScorer
import party.qwer.iris.imagebridge.runtime.memberextract.MemberElementFlattener
import party.qwer.iris.imagebridge.runtime.memberextract.MemberExtractionDiagnostics
import party.qwer.iris.imagebridge.runtime.memberextract.MemberExtractionPlanMapper
import party.qwer.iris.imagebridge.runtime.memberextract.MemberFieldSelector
import party.qwer.iris.imagebridge.runtime.memberextract.MemberReflectionWalker
import party.qwer.iris.imagebridge.runtime.memberextract.RankedContainerCandidate

internal class ChatRoomMemberExtractor(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val reflectionWalker = MemberReflectionWalker()
    private val candidateCollector =
        MemberCandidateCollector(
            reflectionWalker = reflectionWalker,
            flattener = MemberElementFlattener(reflectionWalker),
        )
    private val scorer =
        MemberContainerScorer(
            candidateCollector = candidateCollector,
            fieldSelector = MemberFieldSelector(),
        )
    private val planMapper = MemberExtractionPlanMapper()
    private val diagnostics = MemberExtractionDiagnostics(candidateCollector)

    fun snapshot(
        roomId: Long,
        room: Any,
        expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint> = emptyList(),
        preferredPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan? = null,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val expectedMemberIds = expectedMemberHints.map { it.userId }.toSet()
        check(expectedMemberIds.isNotEmpty()) { "expected member ids required" }
        val expectedNicknames =
            expectedMemberHints
                .mapNotNull { hint ->
                    hint.nickname
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { nickname -> hint.userId to nickname }
                }.toMap(linkedMapOf())
        val containers = candidateCollector.collectContainers(room)

        preferredPlan
            ?.let(planMapper::toInternalPlan)
            ?.let { plan ->
                applyPreferredPlan(
                    roomId = roomId,
                    containers = containers,
                    expectedMemberIds = expectedMemberIds,
                    expectedNicknames = expectedNicknames,
                    preferredPlan = plan,
                )
            }?.let { return it }

        return discoverBestSnapshot(
            roomId = roomId,
            containers = containers,
            expectedMemberIds = expectedMemberIds,
            expectedNicknames = expectedNicknames,
        )
    }

    fun snapshot(
        roomId: Long,
        room: Any,
        expectedMemberIds: Set<Long>,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot =
        snapshot(
            roomId = roomId,
            room = room,
            expectedMemberHints = expectedMemberIds.map { userId -> ImageBridgeProtocol.ChatRoomMemberHint(userId = userId) },
        )

    private fun applyPreferredPlan(
        roomId: Long,
        containers: List<ContainerCandidate>,
        expectedMemberIds: Set<Long>,
        expectedNicknames: Map<Long, String>,
        preferredPlan: ExtractionPlan,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot? {
        val container = containers.firstOrNull { candidate -> candidate.path == preferredPlan.containerPath } ?: return null
        val views = candidateCollector.views(container).filter { it.values.isNotEmpty() }
        if (views.isEmpty()) {
            return null
        }
        val sourceClassName = views.firstOrNull { it.className != "<null>" }?.className
        if (!preferredPlan.sourceClassName.isNullOrBlank() && preferredPlan.sourceClassName != sourceClassName) {
            return null
        }

        val members =
            scorer
                .buildMembers(
                    views = views,
                    userPath = preferredPlan.userIdPath,
                    nicknamePath = preferredPlan.nicknamePath,
                    rolePath = preferredPlan.rolePath,
                    profilePath = preferredPlan.profileImagePath,
                ).filter { member -> member.userId in expectedMemberIds }
        if (members.isEmpty()) {
            return null
        }

        val matchedExpected = expectedMemberIds.count { expectedId -> members.any { member -> member.userId == expectedId } }
        if (matchedExpected == 0) {
            return null
        }
        val expectedNicknameMatches =
            members.count { member ->
                expectedNicknames[member.userId] == member.nickname
            }
        return membersSnapshot(
            roomId = roomId,
            candidate =
                RankedContainerCandidate(
                    plan = preferredPlan,
                    members = members,
                    score = 10_000 + matchedExpected * 400 + expectedNicknameMatches * 600,
                    expectedNicknameMatches = expectedNicknameMatches,
                    matchedExpectedCount = matchedExpected,
                    hasRolePath = preferredPlan.rolePath != null,
                    hasProfilePath = preferredPlan.profileImagePath != null,
                    sourceClassName = sourceClassName,
                    containerType = candidateCollector.typeLabel(container),
                    genericLabelPenalty = 0,
                ),
            confidence = ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH,
            confidenceScore = 10_000 + matchedExpected * 400 + expectedNicknameMatches * 600,
            usedPreferredPlan = true,
            candidateGap = null,
        )
    }

    private fun discoverBestSnapshot(
        roomId: Long,
        containers: List<ContainerCandidate>,
        expectedMemberIds: Set<Long>,
        expectedNicknames: Map<Long, String>,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val ranked =
            containers
                .mapNotNull { scorer.evaluateContainer(it, expectedMemberIds, expectedNicknames) }
                .sortedByDescending { it.score }
        val best =
            ranked.firstOrNull()
                ?: run {
                    diagnostics.logMissingCandidateDiagnostics(roomId, containers, expectedMemberIds, expectedNicknames)
                    error("chatroom member candidates not found")
                }
        val second = ranked.getOrNull(1)
        val candidateGap = second?.let { candidate -> best.score - candidate.score }
        val confidence = scorer.confidenceFor(best, candidateGap)
        return membersSnapshot(
            roomId = roomId,
            candidate = best,
            confidence = confidence,
            confidenceScore = best.score,
            usedPreferredPlan = false,
            candidateGap = candidateGap,
        )
    }

    private fun membersSnapshot(
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
