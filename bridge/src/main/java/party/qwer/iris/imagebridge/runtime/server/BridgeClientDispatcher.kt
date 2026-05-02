package party.qwer.iris.imagebridge.runtime.server

import android.net.LocalSocket
import android.util.Log
import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor

internal class BridgeClientDispatcher(
    private val executorProvider: () -> ExecutorService?,
    private val handlerProvider: () -> ImageBridgeRequestHandler?,
    private val isRunning: () -> Boolean,
    private val peerIdentityValidator: BridgePeerIdentityValidator,
    private val metrics: BridgeMetrics,
) {
    fun dispatch(client: LocalSocket) {
        val executor = executorProvider()
        val handler = handlerProvider()
        if (executor == null || handler == null) {
            metrics.recordBridgeShuttingDown()
            writeFailure(client, "bridge shutting down", ImageBridgeProtocol.ERROR_BRIDGE_SHUTTING_DOWN)
            return
        }
        val peerUid = runCatching { client.peerCredentials.uid }.getOrNull()
        try {
            peerIdentityValidator.validate(peerUid)
        } catch (error: IllegalArgumentException) {
            metrics.recordUnauthorizedClient()
            Log.e(TAG, "unauthorized bridge client uid=$peerUid")
            writeFailure(
                client,
                error.message ?: "unauthorized bridge client",
                ImageBridgeProtocol.ERROR_UNAUTHORIZED,
            )
            return
        }
        try {
            metrics.recordQueuedClientCount((executor as? ThreadPoolExecutor)?.queue?.size?.toLong() ?: 0)
            configureClientReadTimeout(client)
            executor.execute {
                handle(client, handler)
            }
        } catch (error: RejectedExecutionException) {
            Log.e(TAG, "client dispatch rejected", error)
            val errorCode =
                if (isRunning()) {
                    metrics.recordBridgeBusy()
                    ImageBridgeProtocol.ERROR_BRIDGE_BUSY
                } else {
                    metrics.recordBridgeShuttingDown()
                    ImageBridgeProtocol.ERROR_BRIDGE_SHUTTING_DOWN
                }
            val message = if (errorCode == ImageBridgeProtocol.ERROR_BRIDGE_BUSY) "bridge busy" else "bridge shutting down"
            writeFailure(client, message, errorCode)
        }
    }

    private fun handle(
        client: LocalSocket,
        handler: ImageBridgeRequestHandler,
    ) {
        metrics.recordClientStart()
        try {
            val request = ImageBridgeProtocol.readRequestFrame(client.inputStream)
            val response = handler.handle(request)
            ImageBridgeProtocol.writeFrame(client.outputStream, response)
        } catch (error: Exception) {
            val isTimeout = error.message?.contains("timed out", ignoreCase = true) == true
            if (isTimeout) {
                metrics.recordTimeout()
            }
            Log.e(TAG, "client handler error", error)
            runCatching {
                ImageBridgeProtocol.writeFrame(
                    client.outputStream,
                    bridgeFailureResponse(
                        error = error.message ?: "internal error",
                        errorCode = if (isTimeout) ImageBridgeProtocol.ERROR_TIMEOUT else ImageBridgeProtocol.ERROR_INTERNAL,
                    ),
                )
            }
        } finally {
            metrics.recordClientEnd()
            runCatching { client.close() }
        }
    }

    private fun writeFailure(
        client: LocalSocket,
        error: String,
        errorCode: String,
    ) {
        runCatching {
            ImageBridgeProtocol.writeFrame(
                client.outputStream,
                bridgeFailureResponse(error, errorCode),
            )
        }
        runCatching { client.close() }
    }

    private fun configureClientReadTimeout(client: LocalSocket) {
        runCatching {
            client.javaClass
                .getMethod("setSoTimeout", Int::class.javaPrimitiveType)
                .invoke(client, CLIENT_READ_TIMEOUT_MS)
        }.onFailure { error ->
            Log.w(TAG, "failed to set bridge client read timeout: ${error.message}")
        }
    }

    private companion object {
        private const val TAG = "IrisBridge"
        private const val CLIENT_READ_TIMEOUT_MS = 5_000
    }
}
