package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

internal fun ImageBridgeHealthSnapshot.toProtocolResponse(): ImageBridgeProtocol.ImageBridgeResponse =
    ImageBridgeProtocol.ImageBridgeResponse(
        status = ImageBridgeProtocol.STATUS_OK,
        running = running,
        specReady = specStatus.ready,
        checkedAtEpochMs = specStatus.checkedAtEpochMs,
        restartCount = restartCount,
        lastCrashMessage = lastCrashMessage,
        checks = specStatus.checks.map { it.toProtocol() },
        discovery =
            ImageBridgeProtocol.ImageBridgeDiscovery(
                installAttempted = discoverySnapshot.installAttempted,
                hooks = discoverySnapshot.hooks.map { it.toProtocol() },
            ),
        capabilities = capabilities.toProtocol(),
        metrics = metrics,
    )

private fun BridgeSpecCheck.toProtocol(): ImageBridgeProtocol.ImageBridgeCheck =
    ImageBridgeProtocol.ImageBridgeCheck(
        name = name,
        ok = ok,
        detail = detail,
    )

private fun party.qwer.iris.imagebridge.runtime.discovery.DiscoveryHookStatus.toProtocol(): ImageBridgeProtocol.ImageBridgeDiscoveryHook =
    ImageBridgeProtocol.ImageBridgeDiscoveryHook(
        name = name,
        installed = installed,
        installError = installError,
        invocationCount = invocationCount,
        lastSeenEpochMs = lastSeenEpochMs,
        lastSummary = lastSummary,
    )

private fun ImageBridgeCapabilitiesSnapshot.toProtocol(): ImageBridgeProtocol.ImageBridgeCapabilities =
    ImageBridgeProtocol.ImageBridgeCapabilities(
        inspectChatRoom = inspectChatRoom.toProtocol(),
        openChatRoom = openChatRoom.toProtocol(),
        snapshotChatRoomMembers = snapshotChatRoomMembers.toProtocol(),
        sendText = sendText.toProtocol(),
        sendMarkdown = sendMarkdown.toProtocol(),
        karingAot = karingAot.toProtocol(),
    )

private fun ImageBridgeCapabilitySnapshot.toProtocol(): ImageBridgeProtocol.ImageBridgeCapability =
    ImageBridgeProtocol.ImageBridgeCapability(
        supported = supported,
        ready = ready,
        reason = reason,
    )
