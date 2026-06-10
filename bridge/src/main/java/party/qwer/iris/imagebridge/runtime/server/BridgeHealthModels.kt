package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot

internal data class BridgeSpecCheck(
    val name: String,
    val ok: Boolean,
    val detail: String? = null,
)

internal data class BridgeSpecStatus(
    val ready: Boolean,
    val checkedAtEpochMs: Long,
    val checks: List<BridgeSpecCheck>,
)

internal data class ImageBridgeHealthSnapshot(
    val running: Boolean,
    val specStatus: BridgeSpecStatus,
    val discoverySnapshot: BridgeDiscoverySnapshot,
    val capabilities: ImageBridgeCapabilitiesSnapshot = ImageBridgeCapabilitiesSnapshot(),
    val metrics: party.qwer.iris.ImageBridgeProtocol.ImageBridgeMetrics? = null,
    val restartCount: Int,
    val lastCrashMessage: String?,
    val bridgeCoreUnavailable: Boolean = false,
)

internal data class ImageBridgeCapabilitySnapshot(
    val supported: Boolean = false,
    val ready: Boolean = false,
    val reason: String? = null,
)

internal data class ImageBridgeCapabilitiesSnapshot(
    val inspectChatRoom: ImageBridgeCapabilitySnapshot = ImageBridgeCapabilitySnapshot(),
    val openChatRoom: ImageBridgeCapabilitySnapshot = ImageBridgeCapabilitySnapshot(),
    val snapshotChatRoomMembers: ImageBridgeCapabilitySnapshot = ImageBridgeCapabilitySnapshot(),
    val sendText: ImageBridgeCapabilitySnapshot = ImageBridgeCapabilitySnapshot(),
    val sendMarkdown: ImageBridgeCapabilitySnapshot = ImageBridgeCapabilitySnapshot(),
)
