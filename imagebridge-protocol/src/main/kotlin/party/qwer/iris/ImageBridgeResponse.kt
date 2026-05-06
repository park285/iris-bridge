package party.qwer.iris

import kotlinx.serialization.Serializable

@Serializable
data class ImageBridgeResponse(
    val status: String,
    val error: String? = null,
    val errorCode: String? = null,
    val requestId: String? = null,
    val running: Boolean? = null,
    val specReady: Boolean? = null,
    val checkedAtEpochMs: Long? = null,
    val restartCount: Int? = null,
    val lastCrashMessage: String? = null,
    val checks: List<ImageBridgeCheck> = emptyList(),
    val discovery: ImageBridgeDiscovery? = null,
    val inspectionJson: String? = null,
    val memberSnapshot: ChatRoomMembersSnapshot? = null,
    val capabilities: ImageBridgeCapabilities? = null,
    val metrics: ImageBridgeMetrics? = null,
)
