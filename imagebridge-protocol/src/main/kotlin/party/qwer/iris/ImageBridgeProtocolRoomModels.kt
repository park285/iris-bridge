package party.qwer.iris

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRoomSnapshotConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

@Serializable
data class ChatRoomMemberSnapshot(
    val userId: Long,
    val nickname: String,
    val roleCode: Int? = null,
    val profileImageUrl: String? = null,
    val mentionUserId: String? = null,
)

@Serializable
data class ChatRoomMembersSnapshot(
    val roomId: Long,
    val sourcePath: String? = null,
    val sourceClassName: String? = null,
    val scannedAtEpochMs: Long,
    val members: List<ChatRoomMemberSnapshot> = emptyList(),
    val selectedPlan: ChatRoomMemberExtractionPlan? = null,
    val confidence: ChatRoomSnapshotConfidence = ChatRoomSnapshotConfidence.LOW,
    val confidenceScore: Int = 0,
    val usedPreferredPlan: Boolean = false,
    val candidateGap: Int? = null,
)
