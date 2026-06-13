package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.memberEnrichmentMerge
import party.qwer.iris.imagebridge.runtime.core.memberEnrichmentMissingNicknames
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream

internal class ChatRoomMemberSnapshotEnricher(
    private val upstreamFetcher: MemberProfileUpstream?,
) {
    fun enrich(
        snapshot: ImageBridgeProtocol.ChatRoomMembersSnapshot,
        hints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val fetcher = upstreamFetcher ?: return snapshot
        val missingUserIds = BridgeCore.memberEnrichmentMissingNicknames(snapshot.members, hints)
        if (missingUserIds.isEmpty()) {
            return snapshot
        }
        val upstreamProfiles =
            runCatching {
                fetcher.fetchMemberProfiles(snapshot.roomId, missingUserIds)
            }.getOrElse {
                return snapshot
            }
        if (upstreamProfiles.isEmpty()) {
            return snapshot
        }
        val merged = BridgeCore.memberEnrichmentMerge(snapshot, upstreamProfiles.values.toList())
        return snapshot.copy(
            members = merged.members,
            sourcePath = merged.sourcePath,
            confidence = merged.confidence,
            confidenceScore = merged.confidenceScore,
        )
    }
}
