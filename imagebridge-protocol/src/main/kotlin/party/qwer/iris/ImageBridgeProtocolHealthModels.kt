package party.qwer.iris

import kotlinx.serialization.Serializable

@Serializable
data class ImageBridgeCapability(
    val supported: Boolean = false,
    val ready: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class ImageBridgeCapabilities(
    val inspectChatRoom: ImageBridgeCapability = ImageBridgeCapability(),
    val openChatRoom: ImageBridgeCapability = ImageBridgeCapability(),
    val snapshotChatRoomMembers: ImageBridgeCapability = ImageBridgeCapability(),
    val sendText: ImageBridgeCapability = ImageBridgeCapability(),
    val sendMarkdown: ImageBridgeCapability = ImageBridgeCapability(),
)

@Serializable
data class ImageBridgeCheck(
    val name: String,
    val ok: Boolean,
    val detail: String? = null,
)

@Serializable
data class ImageBridgeDiscoveryHook(
    val name: String,
    val installed: Boolean,
    val installError: String? = null,
    val invocationCount: Int = 0,
    val lastSeenEpochMs: Long? = null,
    val lastSummary: String? = null,
)

@Serializable
data class ImageBridgeDiscovery(
    val installAttempted: Boolean = false,
    val hooks: List<ImageBridgeDiscoveryHook> = emptyList(),
)

@Serializable
data class ImageBridgeMetrics(
    val sendSuccess: Long = 0,
    val sendFailure: Long = 0,
    val pathValidationFailure: Long = 0,
    val unauthorizedClient: Long = 0,
    val bridgeBusy: Long = 0,
    val bridgeShuttingDown: Long = 0,
    val timeout: Long = 0,
    val missingRequestId: Long = 0,
    val rejectedClient: Long = 0,
    val activeClient: Long = 0,
    val queuedClient: Long = 0,
    val muxRequestCancelled: Long = 0,
    val muxRequestDeduplicated: Long = 0,
    val muxGoawaySent: Long = 0,
    val lastSendRequestId: String? = null,
    val lastSendStartedAtEpochMs: Long? = null,
    val lastSendCompletedAtEpochMs: Long? = null,
    val lastSendDurationMs: Long? = null,
    val lastSendErrorCode: String? = null,
)
