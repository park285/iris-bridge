package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.resolveBridgeToken
import java.io.InputStream
import java.io.OutputStream

internal class BridgeSocketHandshakeAuthenticator(
    private val expectedToken: String = resolveBridgeToken(),
    private val securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
    private val requireHandshakeRaw: String? = System.getenv("IRIS_BRIDGE_REQUIRE_HANDSHAKE"),
    private val nonceFactory: () -> String = ImageBridgeHandshakeProtocol::newNonce,
) {
    fun authenticate(
        input: InputStream,
        output: OutputStream,
        socketName: String,
    ) {
        if (!isRequired()) return
        try {
            require(expectedToken.isNotBlank()) { ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED }
            val hello = ImageBridgeHandshakeProtocol.readFrame(input)
            val clientNonce = hello.clientNonce.orEmpty()
            if (
                hello.type != ImageBridgeHandshakeProtocol.TYPE_HELLO ||
                hello.protocolVersion != ImageBridgeProtocol.PROTOCOL_VERSION ||
                clientNonce.isBlank()
            ) {
                throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED)
            }
            val serverNonce = nonceFactory()
            ImageBridgeHandshakeProtocol.writeFrame(
                output,
                ImageBridgeHandshakeProtocol.buildServerProof(
                    bridgeToken = expectedToken,
                    clientNonce = clientNonce,
                    serverNonce = serverNonce,
                    socketName = socketName,
                ),
            )
            val clientProof = ImageBridgeHandshakeProtocol.readFrame(input)
            val expectedProof =
                ImageBridgeHandshakeProtocol.clientProof(
                    bridgeToken = expectedToken,
                    clientNonce = clientNonce,
                    serverNonce = serverNonce,
                )
            if (
                clientProof.type != ImageBridgeHandshakeProtocol.TYPE_CLIENT_PROOF ||
                clientProof.protocolVersion != ImageBridgeProtocol.PROTOCOL_VERSION ||
                !ImageBridgeHandshakeProtocol.proofMatches(clientProof.proof, expectedProof)
            ) {
                throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED)
            }
        } catch (error: Exception) {
            throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED, error)
        }
    }

    private fun isRequired(): Boolean {
        val explicitlyRequired =
            when (requireHandshakeRaw?.trim()?.lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> null
            }
        if (explicitlyRequired == true) return true
        if (securityMode == BridgeSecurityMode.DEVELOPMENT) return false
        return true
    }
}
