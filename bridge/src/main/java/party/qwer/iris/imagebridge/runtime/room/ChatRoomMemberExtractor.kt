package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.MemberExtractionContainerData
import party.qwer.iris.imagebridge.runtime.core.memberExtractionEvaluate
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberCandidateCollector
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberElementFlattener
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberExtractionDiagnostics
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
    private val diagnostics = MemberExtractionDiagnostics(candidateCollector)

    fun snapshot(
        roomId: Long,
        room: Any,
        expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint> = emptyList(),
        preferredPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan? = null,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val containers = candidateCollector.collectContainers(room)
        val evaluation =
            BridgeCore.memberExtractionEvaluate(
                containers =
                    containers.map { container ->
                        MemberExtractionContainerData(
                            path = container.path,
                            containerType = candidateCollector.typeLabel(container),
                            views = candidateCollector.views(container),
                        )
                    },
                expectedMemberHints = expectedMemberHints,
                preferredPlan = preferredPlan,
            ) ?: run {
                val expected = expectedMemberHintsFrom(expectedMemberHints)
                diagnostics.logMissingCandidateDiagnostics(roomId, containers, expected.ids, expected.nicknames)
                error("chatroom member candidates not found")
            }
        return ImageBridgeProtocol.ChatRoomMembersSnapshot(
            roomId = roomId,
            sourcePath = evaluation.sourcePath,
            sourceClassName = evaluation.sourceClassName,
            scannedAtEpochMs = clock(),
            members = evaluation.members,
            selectedPlan = evaluation.selectedPlan,
            confidence = evaluation.confidence,
            confidenceScore = evaluation.confidenceScore,
            usedPreferredPlan = evaluation.usedPreferredPlan,
            candidateGap = evaluation.candidateGap,
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
