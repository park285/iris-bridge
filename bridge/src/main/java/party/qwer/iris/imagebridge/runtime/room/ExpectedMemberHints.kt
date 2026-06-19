package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.ImageBridgeProtocol

internal data class ExpectedMemberHints(
    val ids: Set<Long>,
    val nicknames: Map<Long, String>,
    val mentionUserIds: Map<Long, String>,
)

internal fun expectedMemberHintsFrom(hints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>): ExpectedMemberHints =
    ExpectedMemberHints(
        ids = hints.map { it.userId }.toSet(),
        nicknames =
            hints
                .mapNotNull { hint ->
                    hint.nickname
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { nickname -> hint.userId to nickname }
                }.toMap(linkedMapOf()),
        mentionUserIds =
            hints
                .mapNotNull { hint ->
                    hint.mentionUserId
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { mentionUserId -> hint.userId to mentionUserId }
                }.toMap(linkedMapOf()),
    )
