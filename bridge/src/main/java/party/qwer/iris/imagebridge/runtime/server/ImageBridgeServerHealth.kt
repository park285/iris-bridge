package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.currentBridgeCapabilities
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability

internal fun buildImageBridgeHealthSnapshot(
    running: Boolean,
    specStatus: BridgeSpecStatus?,
    registryAvailable: Boolean,
    lastRegistryError: String?,
    textSendCapability: KakaoTextSendCapability?,
    textBridgeSendTextEnabled: Boolean,
    textBridgeSendMarkdownEnabled: Boolean,
    metrics: party.qwer.iris.ImageBridgeProtocol.ImageBridgeMetrics,
    restartCount: Int,
    lastCrashMessage: String?,
): ImageBridgeHealthSnapshot =
    ImageBridgeHealthSnapshot(
        running = running,
        specStatus = specStatus ?: BridgeSpecStatus(ready = false, checkedAtEpochMs = 0L, checks = emptyList()),
        discoverySnapshot = BridgeDiscovery.snapshot(),
        capabilities =
            currentBridgeCapabilities(
                registryAvailable,
                lastRegistryError,
                specStatus?.ready == true,
                textSendCapability,
                textBridgeSendTextEnabled,
                textBridgeSendMarkdownEnabled,
            ),
        metrics = metrics,
        restartCount = restartCount,
        lastCrashMessage = lastCrashMessage,
    )
