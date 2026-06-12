package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeHandshakeFrame
import party.qwer.iris.ImageBridgeHandshakeProtocol

internal object BridgeHandshakeTestFixtures {
    @Suppress("DEPRECATION_ERROR")
    fun buildClientProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
    ): ImageBridgeHandshakeFrame =
        ImageBridgeHandshakeProtocol.buildClientProof(
            bridgeToken = bridgeToken,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
        )

    @Suppress("DEPRECATION_ERROR")
    fun serverProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
        socketName: String,
    ): String =
        ImageBridgeHandshakeProtocol.serverProof(
            bridgeToken = bridgeToken,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
            socketName = socketName,
        )

    @Suppress("DEPRECATION_ERROR")
    fun clientProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
    ): String =
        ImageBridgeHandshakeProtocol.clientProof(
            bridgeToken = bridgeToken,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
        )
}
