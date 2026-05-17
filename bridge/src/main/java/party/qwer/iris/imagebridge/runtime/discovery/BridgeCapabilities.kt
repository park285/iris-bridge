package party.qwer.iris.imagebridge.runtime.discovery

import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitiesSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitySnapshot

internal fun currentBridgeCapabilities(
    registryAvailable: Boolean,
    registryError: String?,
    specReady: Boolean,
    textSendCapability: KakaoTextSendCapability? = null,
    sendTextEnabled: Boolean = true,
    sendMarkdownEnabled: Boolean = true,
    karingAotAvailable: Boolean = false,
    karingAotReason: String? = null,
): ImageBridgeCapabilitiesSnapshot {
    val readinessReason =
        when {
            !registryAvailable -> registryError ?: "chatroom resolver unavailable"
            !specReady -> "bridge spec not ready"
            else -> "capability ready"
        }
    return ImageBridgeCapabilitiesSnapshot(
        inspectChatRoom =
            ImageBridgeCapabilitySnapshot(
                supported = registryAvailable,
                ready = registryAvailable && specReady,
                reason = if (registryAvailable && specReady) null else readinessReason,
            ),
        openChatRoom =
            ImageBridgeCapabilitySnapshot(
                supported = true,
                ready = true,
            ),
        snapshotChatRoomMembers =
            ImageBridgeCapabilitySnapshot(
                supported = registryAvailable,
                ready = registryAvailable && specReady,
                reason = if (registryAvailable && specReady) null else readinessReason,
            ),
        sendText =
            textCapabilitySnapshot(
                registryAvailable = registryAvailable,
                specReady = specReady,
                readinessReason = readinessReason,
                textSendCapability = textSendCapability,
                enabled = sendTextEnabled,
                disabledReason = "text bridge send_text disabled",
            ),
        sendMarkdown =
            textCapabilitySnapshot(
                registryAvailable = registryAvailable,
                specReady = specReady,
                readinessReason = readinessReason,
                textSendCapability = textSendCapability,
                enabled = sendMarkdownEnabled,
                disabledReason = "text bridge send_markdown disabled",
            ),
        karingAot =
            ImageBridgeCapabilitySnapshot(
                supported = true,
                ready = karingAotAvailable,
                reason = if (karingAotAvailable) null else karingAotReason ?: "karing aot provider unavailable",
            ),
    )
}

private fun textCapabilitySnapshot(
    registryAvailable: Boolean,
    specReady: Boolean,
    readinessReason: String,
    textSendCapability: KakaoTextSendCapability?,
    enabled: Boolean,
    disabledReason: String,
): ImageBridgeCapabilitySnapshot =
    ImageBridgeCapabilitySnapshot(
        supported = textSendCapability?.supported == true,
        ready = registryAvailable && specReady && enabled && textSendCapability?.ready == true,
        reason =
            when {
                !registryAvailable || !specReady -> readinessReason
                !enabled -> disabledReason
                textSendCapability?.ready == true -> null
                else -> textSendCapability?.reason ?: "text sender unavailable"
            },
    )
