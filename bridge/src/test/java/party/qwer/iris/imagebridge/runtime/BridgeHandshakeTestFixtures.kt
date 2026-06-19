package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeHandshakeFrame
import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.ImageBridgeProtocol
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object BridgeHandshakeTestFixtures {
    private const val SERVER_DOMAIN = "iris-bridge-server"
    private const val CLIENT_DOMAIN = "iris-bridge-client"
    private const val HMAC_SHA256 = "HmacSHA256"

    fun buildClientProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
    ): ImageBridgeHandshakeFrame =
        ImageBridgeHandshakeFrame(
            type = ImageBridgeHandshakeProtocol.TYPE_CLIENT_PROOF,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            proof = clientProof(bridgeToken, clientNonce, serverNonce),
        )

    fun serverProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
        socketName: String,
    ): String = hmacHex(bridgeToken, SERVER_DOMAIN, clientNonce, serverNonce, socketName)

    fun clientProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
    ): String = hmacHex(bridgeToken, CLIENT_DOMAIN, clientNonce, serverNonce)

    private fun hmacHex(
        bridgeToken: String,
        domain: String,
        vararg parts: String,
    ): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(bridgeToken.toByteArray(Charsets.UTF_8), HMAC_SHA256))
        updateLengthPrefixed(mac, domain.toByteArray(Charsets.UTF_8))
        parts.forEach { updateLengthPrefixed(mac, it.toByteArray(Charsets.UTF_8)) }
        return mac.doFinal().toHex()
    }

    private fun updateLengthPrefixed(
        mac: Mac,
        part: ByteArray,
    ) {
        mac.update(part.size.toString().toByteArray(Charsets.UTF_8))
        mac.update(0.toByte())
        mac.update(part)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
