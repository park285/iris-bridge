package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberCandidateCollector
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberContainerScorer
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberElementFlattener
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberExtractionDiagnostics
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberExtractionPlanMapper
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberFieldSelector
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberReflectionWalker

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
    private val snapshotBuilder = ChatRoomMemberSnapshotBuilder(clock, scorer, planMapper, diagnostics)
    private val preferredPlanApplier = PreferredMemberPlanApplier(candidateCollector, scorer, snapshotBuilder)

    fun snapshot(
        roomId: Long,
        room: Any,
        expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint> = emptyList(),
        preferredPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan? = null,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val expected = expectedMemberHintsFrom(expectedMemberHints)
        val containers = candidateCollector.collectContainers(room)

        preferredPlan
            ?.let(planMapper::toInternalPlan)
            ?.let { plan ->
                preferredPlanApplier.apply(
                    roomId = roomId,
                    containers = containers,
                    expected = expected,
                    preferredPlan = plan,
                )
            }?.let { return it }

        return snapshotBuilder.discoverBestSnapshot(
            roomId = roomId,
            containers = containers,
            expected = expected,
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
}
