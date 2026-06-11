package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile

internal class ChatRoomMemberSnapshotEnricher(
    private val upstreamFetcher: MemberProfileUpstream?,
) {
    fun enrich(
        snapshot: ImageBridgeProtocol.ChatRoomMembersSnapshot,
        hints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot {
        val fetcher = upstreamFetcher ?: return snapshot
        val missingUserIds = missingNicknameUserIds(snapshot, hints)
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
        return snapshot.copy(
            members = mergeMembers(snapshot.members, upstreamProfiles),
            sourcePath = listOfNotNull(snapshot.sourcePath, "upstream:member").joinToString("+"),
            confidence =
                when (snapshot.confidence) {
                    ImageBridgeProtocol.ChatRoomSnapshotConfidence.LOW ->
                        ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM
                    else -> snapshot.confidence
                },
            confidenceScore = snapshot.confidenceScore + upstreamProfiles.size,
        )
    }

    private fun missingNicknameUserIds(
        snapshot: ImageBridgeProtocol.ChatRoomMembersSnapshot,
        hints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    ): Set<Long> {
        val hinted =
            hints
                .filter { hint -> memberNicknameNeedsUpstream(hint.userId, hint.nickname) }
                .map { it.userId }
                .toSet()
        val fromSnapshot =
            snapshot.members
                .filter { member -> memberNicknameNeedsUpstream(member.userId, member.nickname) }
                .map { it.userId }
                .toSet()
        return hinted + fromSnapshot
    }

    private fun mergeMembers(
        members: List<ImageBridgeProtocol.ChatRoomMemberSnapshot>,
        upstreamProfiles: Map<Long, UpstreamMemberProfile>,
    ): List<ImageBridgeProtocol.ChatRoomMemberSnapshot> {
        if (members.isEmpty()) {
            return upstreamProfiles.values.map(::toSnapshotMember)
        }
        val merged =
            members.map { member ->
                val upstream = upstreamProfiles[member.userId] ?: return@map member
                if (!memberNicknameNeedsUpstream(member.userId, member.nickname)) {
                    return@map member
                }
                member.copy(
                    nickname = upstream.nickName,
                    profileImageUrl = member.profileImageUrl ?: upstream.profileImageUrl,
                )
            }
        val knownIds = merged.map { it.userId }.toSet()
        val appended =
            upstreamProfiles.values
                .filter { profile -> profile.userId !in knownIds }
                .map(::toSnapshotMember)
        return merged + appended
    }

    private fun toSnapshotMember(profile: UpstreamMemberProfile): ImageBridgeProtocol.ChatRoomMemberSnapshot =
        ImageBridgeProtocol.ChatRoomMemberSnapshot(
            userId = profile.userId,
            nickname = profile.nickName,
            profileImageUrl = profile.profileImageUrl,
        )
}
