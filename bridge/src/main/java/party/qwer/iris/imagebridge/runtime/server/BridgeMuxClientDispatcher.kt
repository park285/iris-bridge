package party.qwer.iris.imagebridge.runtime.server

import android.net.LocalSocket
import android.util.Log
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.ExecutorService

internal class BridgeMuxClientDispatcher(
    private val executorProvider: () -> ExecutorService?,
    private val handlerProvider: () -> ImageBridgeRequestHandler?,
    private val isRunning: () -> Boolean,
    private val peerIdentityValidator: BridgePeerIdentityValidator,
    private val metrics: BridgeMetrics,
    private val socketWrapper: (LocalSocket) -> BridgeMuxSocket = ::LocalBridgeMuxSocket,
) {
    fun dispatch(client: LocalSocket) {
        dispatch(socketWrapper(client))
    }

    internal fun dispatch(client: BridgeMuxSocket) {
        val executor = executorProvider()
        val handler = handlerProvider()
        if (executor == null || handler == null) {
            metrics.recordBridgeShuttingDown()
            writeGoaway(client, "bridge shutting down", ImageBridgeProtocol.ERROR_BRIDGE_SHUTTING_DOWN)
            return
        }
        try {
            peerIdentityValidator.validate(client.peerUid)
        } catch (error: IllegalArgumentException) {
            metrics.recordUnauthorizedClient()
            Log.e(TAG, "unauthorized bridge mux client uid=${client.peerUid}")
            writeGoaway(
                client,
                error.message ?: "unauthorized bridge client",
                ImageBridgeProtocol.ERROR_UNAUTHORIZED,
            )
            return
        }
        client.setReadTimeout(CLIENT_READ_TIMEOUT_MS)
        Thread(
            {
                BridgeMuxSession(
                    client = client,
                    executor = executor,
                    handler = handler,
                    isRunning = isRunning,
                    metrics = metrics,
                ).run()
            },
            "iris-bridge-mux-client",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun writeGoaway(
        client: BridgeMuxSocket,
        error: String,
        errorCode: String,
    ) {
        runCatching {
            ImageBridgeMuxProtocol.writeFrame(
                client.outputStream,
                ImageBridgeMuxFrame(
                    type = ImageBridgeMuxProtocol.TYPE_GOAWAY,
                    error = error,
                    errorCode = errorCode,
                ),
            )
        }
        runCatching { client.close() }
    }

    private companion object {
        private const val TAG = "IrisBridge"
        private const val CLIENT_READ_TIMEOUT_MS = 5_000
    }
}
