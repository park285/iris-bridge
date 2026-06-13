package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import java.util.concurrent.ExecutorService

internal fun newBridgeMuxClientDispatcher(
    executorProvider: () -> ExecutorService?,
    handlerProvider: () -> ImageBridgeRequestHandler?,
    bridgeCoreProvider: () -> BridgeCoreRuntime?,
    isRunning: () -> Boolean,
    peerIdentityValidator: BridgePeerIdentityValidator,
    metrics: BridgeMetrics,
    sessionAdmission: BridgeSessionAdmission = newBridgeSessionAdmission(metrics),
): BridgeMuxClientDispatcher =
    BridgeMuxClientDispatcher(
        executorProvider = executorProvider,
        handlerProvider = handlerProvider,
        bridgeCoreProvider = bridgeCoreProvider,
        isRunning = isRunning,
        peerIdentityValidator = peerIdentityValidator,
        metrics = metrics,
        sessionAdmission = sessionAdmission,
    )
