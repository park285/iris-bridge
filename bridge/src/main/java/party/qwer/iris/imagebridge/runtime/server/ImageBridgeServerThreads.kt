package party.qwer.iris.imagebridge.runtime.server

internal fun startOneShotBridgeServerThread(
    dispatcher: BridgeClientDispatcher,
    isRunning: () -> Boolean,
    restartDelayMs: (Int) -> Long,
    recordFailure: (String) -> Unit,
    sleepBeforeRestart: (Long) -> Unit,
    shutdownExecutor: () -> Unit,
) {
    Thread(
        {
            BridgeOneShotServerLoop(
                dispatcher = dispatcher,
                isRunning = isRunning,
                restartDelayMs = restartDelayMs,
                recordFailure = recordFailure,
                sleepBeforeRestart = sleepBeforeRestart,
                shutdownExecutor = shutdownExecutor,
            ).run()
        },
        "iris-bridge-server",
    ).startDaemon()
}

internal fun startMuxBridgeServerThread(
    dispatcher: BridgeMuxClientDispatcher,
    isRunning: () -> Boolean,
    restartDelayMs: (Int) -> Long,
    recordFailure: (String) -> Unit,
    sleepBeforeRestart: (Long) -> Unit,
) {
    Thread(
        {
            BridgeMuxServerLoop(
                dispatcher = dispatcher,
                isRunning = isRunning,
                restartDelayMs = restartDelayMs,
                recordFailure = recordFailure,
                sleepBeforeRestart = sleepBeforeRestart,
            ).run()
        },
        "iris-bridge-mux-server",
    ).startDaemon()
}

internal fun Thread.startDaemon() {
    isDaemon = true
    start()
}
