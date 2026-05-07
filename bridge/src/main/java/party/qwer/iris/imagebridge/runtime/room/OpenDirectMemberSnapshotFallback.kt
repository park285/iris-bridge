package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.room.memberextract.ContainerCandidate
import party.qwer.iris.imagebridge.runtime.room.memberextract.MemberCandidateCollector
import party.qwer.iris.imagebridge.runtime.room.memberextract.PrimitiveValue
import party.qwer.iris.imagebridge.runtime.room.memberextract.looksLikeNickname
import party.qwer.iris.imagebridge.runtime.room.memberextract.primitiveLongValue

internal class OpenDirectMemberSnapshotFallback(
    private val clock: () -> Long,
    private val candidateCollector: MemberCandidateCollector,
) {
    fun snapshot(
        roomId: Long,
        containers: List<ContainerCandidate>,
        expected: ExpectedMemberHints,
    ): ImageBridgeProtocol.ChatRoomMembersSnapshot? {
        val userId = openLinkBackupUserId(containers, expected.ids) ?: return null
        val nickname = welcomeNickname(containers) ?: return null
        return ImageBridgeProtocol.ChatRoomMembersSnapshot(
            roomId = roomId,
            sourcePath = "${userId.sourcePath}+${nickname.sourcePath}",
            scannedAtEpochMs = clock(),
            members = listOf(ImageBridgeProtocol.ChatRoomMemberSnapshot(userId = userId.value, nickname = nickname.value)),
            confidence = ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM,
            confidenceScore = OPEN_DIRECT_FALLBACK_CONFIDENCE_SCORE,
        )
    }

    private fun openLinkBackupUserId(
        containers: List<ContainerCandidate>,
        expectedIds: Set<Long>,
    ): FallbackValue<Long>? {
        val candidates =
            containers
                .flatMap { container ->
                    candidateCollector.views(container).flatMap { view ->
                        val key = (view.values["key"] as? PrimitiveValue.StringValue)?.value?.trim() ?: return@flatMap emptyList()
                        if (!key.equals(OPEN_LINK_CHAT_MEMBER_ID_BACKUP_KEY, ignoreCase = true)) return@flatMap emptyList()
                        val value = primitiveLongValue(view.values["value"])?.takeIf { it > 0L } ?: return@flatMap emptyList()
                        listOf(FallbackValue(value, "${container.path}.value"))
                    }
                }.distinctBy { it.value }
                .filter { candidate -> expectedIds.isEmpty() || candidate.value in expectedIds }
        return candidates.singleOrNull()
    }

    private fun welcomeNickname(containers: List<ContainerCandidate>): FallbackValue<String>? {
        val candidates =
            containers
                .flatMap(::welcomeNicknameCandidates)
                .distinctBy { it.value }
        return candidates.singleOrNull()
    }

    private fun welcomeNicknameCandidates(container: ContainerCandidate): List<FallbackValue<String>> =
        candidateCollector.views(container).flatMap { view ->
            view.values.mapNotNull { (path, value) -> welcomeNicknameValue(container.path, path, value) }
        }

    private fun welcomeNicknameValue(
        containerPath: String,
        path: String,
        value: PrimitiveValue,
    ): FallbackValue<String>? {
        val text = (value as? PrimitiveValue.StringValue)?.value?.trim() ?: return null
        val nickname =
            WELCOME_TO_PATTERN
                .matchEntire(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?: return null
        return nickname
            .takeIf(::looksLikeNickname)
            ?.let { FallbackValue(it, "$containerPath.$path") }
    }

    private data class FallbackValue<T>(
        val value: T,
        val sourcePath: String,
    )
}

private const val OPEN_DIRECT_FALLBACK_CONFIDENCE_SCORE = 650
private const val OPEN_LINK_CHAT_MEMBER_ID_BACKUP_KEY = "openLinkChatMemberIdBackup"
private val WELCOME_TO_PATTERN = Regex("""Welcome to ['‘’"](.+?)['‘’"]\.?""")
