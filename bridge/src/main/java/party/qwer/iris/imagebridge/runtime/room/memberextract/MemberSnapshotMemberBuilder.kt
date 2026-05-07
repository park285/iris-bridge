package party.qwer.iris.imagebridge.runtime.room.memberextract

import party.qwer.iris.ImageBridgeProtocol

internal fun buildChatRoomMembers(
    views: List<ElementView>,
    userPath: String,
    nicknamePath: String,
    rolePath: String?,
    profilePath: String?,
    mentionUserIdPath: String?,
    expectedNicknames: Map<Long, String>,
    mentionUserIds: Map<Long, String>,
    fieldSelector: MemberFieldSelector,
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
        if (nickname == userId.toString() && expectedNicknames[userId] != nickname) return@forEach
        val candidate =
            ImageBridgeProtocol.ChatRoomMemberSnapshot(
                userId = userId,
                nickname = nickname,
                roleCode = rolePath?.let { path -> fieldSelector.parseRoleCode(view.values[path]) },
                profileImageUrl = profilePath?.let { path -> profileImageValue(view, path, fieldSelector) },
                mentionUserId = mentionUserIdValue(view, mentionUserIdPath, mentionUserIds, userId, nickname, fieldSelector),
            )
        val current = deduped[userId]
        if (current == null || memberCompleteness(candidate) > memberCompleteness(current)) {
            deduped[userId] = candidate
        }
    }
    return deduped.values.toList()
}

private fun profileImageValue(
    view: ElementView,
    path: String,
    fieldSelector: MemberFieldSelector,
): String? =
    (view.values[path] as? PrimitiveValue.StringValue)
        ?.value
        ?.trim()
        ?.takeIf(fieldSelector::looksLikeProfileUrl)

private fun mentionUserIdValue(
    view: ElementView,
    path: String?,
    mentionUserIds: Map<Long, String>,
    userId: Long,
    nickname: String,
    fieldSelector: MemberFieldSelector,
): String? =
    mentionUserIds[userId]
        ?: path
            ?.let { mentionPath -> (view.values[mentionPath] as? PrimitiveValue.StringValue)?.value?.trim() }
            ?.takeIf { value -> fieldSelector.looksLikeMentionUserIdValue(value, userId, nickname) }

private fun memberCompleteness(member: ImageBridgeProtocol.ChatRoomMemberSnapshot): Int = listOfNotNull(member.roleCode, member.profileImageUrl?.takeIf(String::isNotBlank)).size
