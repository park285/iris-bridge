package party.qwer.iris.imagebridge.runtime.discovery

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreEnvelope
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitiesSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitySnapshot
import party.qwer.iris.imagebridge.runtime.core.currentBridgeCapabilities as nativeCurrentBridgeCapabilities

private const val CAPABILITIES_POLICY_UNAVAILABLE_REASON = "bridge core unavailable to evaluate bridge capabilities"

internal typealias BridgeCapabilitiesNativePolicy = (
    registryAvailable: Boolean,
    registryError: String?,
    specReady: Boolean,
    textSupported: Boolean,
    textReady: Boolean,
    textReason: String?,
    sendTextEnabled: Boolean,
    sendMarkdownEnabled: Boolean,
) -> BridgeCoreEnvelope?

internal fun currentBridgeCapabilities(
    registryAvailable: Boolean,
    registryError: String?,
    specReady: Boolean,
    textSendCapability: KakaoTextSendCapability? = null,
    sendTextEnabled: Boolean = true,
    sendMarkdownEnabled: Boolean = true,
    nativeCapabilities: BridgeCapabilitiesNativePolicy = ::nativeBridgeCapabilities,
): ImageBridgeCapabilitiesSnapshot {
    nativeCapabilities(
        registryAvailable,
        registryError,
        specReady,
        textSendCapability?.supported == true,
        textSendCapability?.ready == true,
        textSendCapability?.reason,
        sendTextEnabled,
        sendMarkdownEnabled,
    )?.takeIf { envelope -> envelope.isOk }
        ?.capabilitiesSnapshot()
        ?.let { capabilities -> return capabilities }

    return unavailableCapabilities()
}

private fun nativeBridgeCapabilities(
    registryAvailable: Boolean,
    registryError: String?,
    specReady: Boolean,
    textSupported: Boolean,
    textReady: Boolean,
    textReason: String?,
    sendTextEnabled: Boolean,
    sendMarkdownEnabled: Boolean,
): BridgeCoreEnvelope? =
    with(BridgeCore) {
        nativeCurrentBridgeCapabilities(
            registryAvailable = registryAvailable,
            registryError = registryError,
            specReady = specReady,
            textSupported = textSupported,
            textReady = textReady,
            textReason = textReason,
            sendTextEnabled = sendTextEnabled,
            sendMarkdownEnabled = sendMarkdownEnabled,
        )
    }

private fun BridgeCoreEnvelope.capabilitiesSnapshot(): ImageBridgeCapabilitiesSnapshot? =
    ImageBridgeCapabilitiesSnapshot(
        inspectChatRoom = capabilitySnapshot("inspectChatRoom") ?: return null,
        openChatRoom = capabilitySnapshot("openChatRoom") ?: return null,
        snapshotChatRoomMembers = capabilitySnapshot("snapshotChatRoomMembers") ?: return null,
        sendText = capabilitySnapshot("sendText") ?: return null,
        sendMarkdown = capabilitySnapshot("sendMarkdown") ?: return null,
    )

private fun BridgeCoreEnvelope.capabilitySnapshot(prefix: String): ImageBridgeCapabilitySnapshot? =
    ImageBridgeCapabilitySnapshot(
        supported = strictBool("${prefix}Supported") ?: return null,
        ready = strictBool("${prefix}Ready") ?: return null,
        reason = string("${prefix}Reason"),
    )

private fun unavailableCapabilities(): ImageBridgeCapabilitiesSnapshot {
    val unavailable = unavailableCapability()
    return ImageBridgeCapabilitiesSnapshot(
        inspectChatRoom = unavailable,
        openChatRoom = unavailable,
        snapshotChatRoomMembers = unavailable,
        sendText = unavailable,
        sendMarkdown = unavailable,
    )
}

private fun unavailableCapability(): ImageBridgeCapabilitySnapshot =
    ImageBridgeCapabilitySnapshot(
        supported = false,
        ready = false,
        reason = CAPABILITIES_POLICY_UNAVAILABLE_REASON,
    )
