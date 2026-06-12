package party.qwer.iris

internal object HandshakeProofTestFixtures {
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

    @Suppress("DEPRECATION_ERROR")
    fun proofMatches(
        actual: String?,
        expected: String,
    ): Boolean = ImageBridgeHandshakeProtocol.proofMatches(actual, expected)
}
