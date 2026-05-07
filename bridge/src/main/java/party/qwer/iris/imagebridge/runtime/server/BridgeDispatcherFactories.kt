package party.qwer.iris.imagebridge.runtime.server

import java.util.concurrent.ExecutorService

internal fun newBridgeMuxClientDispatcher(
    executorProvider: () -> ExecutorService?,
    handlerProvider: () -> ImageBridgeRequestHandler?,
    isRunning: () -> Boolean,
    peerIdentityValidator: BridgePeerIdentityValidator,
    metrics: BridgeMetrics,
): BridgeMuxClientDispatcher =
    BridgeMuxClientDispatcher(
        executorProvider = executorProvider,
        handlerProvider = handlerProvider,
        isRunning = isRunning,
        peerIdentityValidator = peerIdentityValidator,
        metrics = metrics,
    )
