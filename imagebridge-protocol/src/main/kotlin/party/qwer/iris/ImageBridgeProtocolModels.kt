package party.qwer.iris

import kotlinx.serialization.Serializable

@Serializable
data class ImageBridgeRequest(
    val action: String,
    val protocolVersion: Int? = null,
    val roomId: Long? = null,
    val imagePaths: List<String> = emptyList(),
    val message: String? = null,
    val markdown: Boolean? = null,
    val mentionsJson: String? = null,
    val attachmentJson: String? = null,
    val threadId: Long? = null,
    val threadScope: Int? = null,
    val requestId: String? = null,
    val token: String? = null,
    val memberIds: List<Long> = emptyList(),
    val memberHints: List<ChatRoomMemberHint> = emptyList(),
    val preferredMemberPlan: ChatRoomMemberExtractionPlan? = null,
)

@Serializable
data class ChatRoomMemberHint(
    val userId: Long,
    val nickname: String? = null,
    val mentionUserId: String? = null,
)

@Serializable
data class ChatRoomMemberExtractionPlan(
    val containerPath: String,
    val sourceClassName: String? = null,
    val userIdPath: String,
    val nicknamePath: String,
    val rolePath: String? = null,
    val profileImagePath: String? = null,
    val mentionUserIdPath: String? = null,
    val fingerprint: String,
    val version: Int = 1,
)
