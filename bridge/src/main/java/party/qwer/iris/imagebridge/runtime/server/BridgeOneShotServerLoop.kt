package party.qwer.iris.imagebridge.runtime.server

import android.net.LocalServerSocket
import android.util.Log
import party.qwer.iris.IrisRuntimePathPolicy

internal class BridgeOneShotServerLoop(
    private val dispatcher: BridgeClientDispatcher,
    private val isRunning: () -> Boolean,
    private val restartDelayMs: (Int) -> Long,
    private val recordFailure: (String) -> Unit,
    private val sleepBeforeRestart: (Long) -> Unit,
    private val shutdownExecutor: () -> Unit,
    private val socketNameProvider: () -> String = { IrisRuntimePathPolicy.resolve().imageBridgeSocketName },
) {
    fun run() {
        var consecutiveFailures = 0
        try {
            while (isRunning()) {
                try {
                    serve()
                    if (isRunning()) {
                        val delayMs = restartDelayMs(++consecutiveFailures)
                        recordFailure("server loop exited unexpectedly")
                        Log.e(TAG, "bridge server stopped unexpectedly; restarting in ${delayMs}ms")
                        sleepBeforeRestart(delayMs)
                    }
                } catch (error: Exception) {
                    if (!isRunning()) break
                    consecutiveFailures += 1
                    recordFailure(error.message ?: error.javaClass.name)
                    val delayMs = restartDelayMs(consecutiveFailures)
                    Log.e(TAG, "bridge server crashed; restarting in ${delayMs}ms", error)
                    sleepBeforeRestart(delayMs)
                }
            }
        } finally {
            shutdownExecutor()
        }
    }

    private fun serve() {
        val socketName = socketNameProvider()
        val serverSocket = LocalServerSocket(socketName)
        try {
            Log.i(TAG, "bridge server listening on @$socketName")
            while (isRunning()) {
                dispatcher.dispatch(serverSocket.accept())
            }
        } finally {
            runCatching { serverSocket.close() }
            Log.i(TAG, "bridge server socket closed")
        }
    }

    private companion object {
        private const val TAG = "IrisBridge"
    }
}
