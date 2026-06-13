package party.qwer.iris

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object HandshakeProofTestFixtures {
    private const val SERVER_DOMAIN = "iris-bridge-server"
    private const val CLIENT_DOMAIN = "iris-bridge-client"
    private const val HMAC_SHA256 = "HmacSHA256"

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

    fun proofMatches(
        actual: String?,
        expected: String,
    ): Boolean = MessageDigest.isEqual((actual ?: "").toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

    private fun hmacHex(
        bridgeToken: String,
        domain: String,
        vararg parts: String,
    ): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(bridgeToken.toByteArray(Charsets.UTF_8), HMAC_SHA256))
        mac.update(domain.toByteArray(Charsets.UTF_8))
        parts.forEach { mac.update(it.toByteArray(Charsets.UTF_8)) }
        return mac.doFinal().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
