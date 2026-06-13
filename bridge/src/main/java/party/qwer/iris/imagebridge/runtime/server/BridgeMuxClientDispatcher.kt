package party.qwer.iris.imagebridge.runtime.server

import android.net.LocalSocket
import android.util.Log
import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.IrisRuntimePathPolicy
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import java.util.concurrent.ExecutorService

internal class BridgeMuxClientDispatcher(
    private val executorProvider: () -> ExecutorService?,
    private val handlerProvider: () -> ImageBridgeRequestHandler?,
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
    private val isRunning: () -> Boolean,
    private val peerIdentityValidator: BridgePeerIdentityValidator,
    private val metrics: BridgeMetrics,
    private val sessionAdmission: BridgeSessionAdmission = newBridgeSessionAdmission(metrics),
    private val socketWrapper: (LocalSocket) -> BridgeMuxSocket = ::LocalBridgeMuxSocket,
    private val handshakeAuthenticator: BridgeSocketHandshakeAuthenticator = BridgeSocketHandshakeAuthenticator(),
    private val socketNameProvider: () -> String = { IrisRuntimePathPolicy.resolve().imageBridgeMuxSocketName },
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
        val bridgeCore = bridgeCoreProvider()
        if (bridgeCore == null) {
            metrics.recordBridgeShuttingDown()
            writeGoaway(client, "bridge shutting down", ImageBridgeProtocol.ERROR_BRIDGE_SHUTTING_DOWN)
            return
        }
        client.setReadTimeout(CLIENT_READ_TIMEOUT_MS)
        val admitted = sessionAdmission.tryExecute { runSession(client, executor, handler, bridgeCore) }
        if (!admitted) {
            metrics.recordBridgeBusy()
            writeGoaway(client, "bridge busy", ImageBridgeProtocol.ERROR_BRIDGE_BUSY)
        }
    }

    private fun runSession(
        client: BridgeMuxSocket,
        executor: ExecutorService,
        handler: ImageBridgeRequestHandler,
        bridgeCore: BridgeCoreRuntime,
    ) {
        val authenticated =
            try {
                handshakeAuthenticator.authenticate(client.inputStream, client.outputStream, socketNameProvider())
                true
            } catch (_: Exception) {
                Log.e(TAG, ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED)
                writeGoaway(
                    client,
                    ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED,
                    ImageBridgeProtocol.ERROR_UNAUTHORIZED,
                )
                false
            }
        if (authenticated) {
            val muxSession = bridgeCore.createMuxSession(BRIDGE_MUX_DEFAULT_MAX_IN_FLIGHT)
            if (muxSession == null) {
                writeGoaway(client, "bridge core unavailable", ImageBridgeProtocol.ERROR_INTERNAL)
                return
            }
            BridgeMuxSession(
                client = client,
                muxSession = muxSession,
                executor = executor,
                handler = handler,
                isRunning = isRunning,
                metrics = metrics,
            ).run()
        }
    }

    private fun writeGoaway(
        client: BridgeMuxSocket,
        error: String,
        errorCode: String,
    ) {
        metrics.recordMuxGoawaySent()
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
        private const val CLIENT_READ_TIMEOUT_MS = 30_000
    }
}
