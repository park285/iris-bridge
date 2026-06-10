package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot
import party.qwer.iris.imagebridge.runtime.discovery.currentBridgeCapabilities
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
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
    bridgeCoreUnavailable: Boolean = false,
    discoverySnapshot: BridgeDiscoverySnapshot = defaultBridgeDiscovery.snapshot(),
): ImageBridgeHealthSnapshot =
    ImageBridgeHealthSnapshot(
        running = running,
        specStatus = specStatus ?: BridgeSpecStatus(ready = false, checkedAtEpochMs = 0L, checks = emptyList()),
        discoverySnapshot = discoverySnapshot,
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
        bridgeCoreUnavailable = bridgeCoreUnavailable,
    )

internal fun ImageBridgeServer.healthSnapshot(): ImageBridgeHealthSnapshot =
    buildImageBridgeHealthSnapshot(
        running = running.get(),
        specStatus = specStatus.get(),
        registryAvailable = registryAvailable.get(),
        lastRegistryError = lastRegistryError.get(),
        textSendCapability = textSendCapability.get(),
        textBridgeSendTextEnabled = textBridgeSendTextEnabled.get(),
        textBridgeSendMarkdownEnabled = textBridgeSendMarkdownEnabled.get(),
        metrics = bridgeMetrics.snapshot(),
        restartCount = restartCount.get(),
        lastCrashMessage = lastCrashMessage.get(),
        bridgeCoreUnavailable = bridgeCoreUnavailable.get(),
        discoverySnapshot = discoverySnapshotProvider(),
    )
