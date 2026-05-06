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
                consecutiveFailures = runOnce(consecutiveFailures) ?: break
            }
        } finally {
            shutdownExecutor()
        }
    }

    private fun runOnce(consecutiveFailures: Int): Int? =
        try {
            serve()
            if (!isRunning()) consecutiveFailures else restartAfterUnexpectedExit(consecutiveFailures)
        } catch (error: Exception) {
            if (!isRunning()) null else restartAfterCrash(consecutiveFailures, error)
        }

    private fun restartAfterUnexpectedExit(consecutiveFailures: Int): Int {
        val nextFailures = consecutiveFailures + 1
        val delayMs = restartDelayMs(nextFailures)
        recordFailure("server loop exited unexpectedly")
        Log.e(TAG, "bridge server stopped unexpectedly; restarting in ${delayMs}ms")
        sleepBeforeRestart(delayMs)
        return nextFailures
    }

    private fun restartAfterCrash(
        consecutiveFailures: Int,
        error: Exception,
    ): Int {
        val nextFailures = consecutiveFailures + 1
        recordFailure(error.message ?: error.javaClass.name)
        val delayMs = restartDelayMs(nextFailures)
        Log.e(TAG, "bridge server crashed; restarting in ${delayMs}ms", error)
        sleepBeforeRestart(delayMs)
        return nextFailures
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
