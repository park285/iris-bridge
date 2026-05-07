package party.qwer.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ImageBridgeHandshakeProtocol {
    const val TYPE_HELLO = "hello"
    const val TYPE_SERVER_PROOF = "server_proof"
    const val TYPE_CLIENT_PROOF = "client_proof"
    const val AUTHENTICATION_FAILED = "bridge authentication failed"

    private const val SERVER_DOMAIN = "iris-bridge-server"
    private const val CLIENT_DOMAIN = "iris-bridge-client"
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val NONCE_BYTES = 32
    private val secureRandom = SecureRandom()
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    fun buildHello(
        clientNonce: String,
        socketName: String,
        timestampMs: Long,
    ): ImageBridgeHandshakeFrame =
        ImageBridgeHandshakeFrame(
            type = TYPE_HELLO,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            clientNonce = clientNonce,
            socketName = socketName,
            timestampMs = timestampMs,
        )

    fun buildServerProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
        socketName: String,
    ): ImageBridgeHandshakeFrame =
        ImageBridgeHandshakeFrame(
            type = TYPE_SERVER_PROOF,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            serverNonce = serverNonce,
            proof = serverProof(bridgeToken, clientNonce, serverNonce, socketName),
        )

    fun buildClientProof(
        bridgeToken: String,
        clientNonce: String,
        serverNonce: String,
    ): ImageBridgeHandshakeFrame =
        ImageBridgeHandshakeFrame(
            type = TYPE_CLIENT_PROOF,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            proof = clientProof(bridgeToken, clientNonce, serverNonce),
        )

    fun newNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

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

    fun writeFrame(
        output: OutputStream,
        frame: ImageBridgeHandshakeFrame,
    ) = LengthPrefixedFrameCodec.writePayload(output, json.encodeToString(frame))

    fun readFrame(input: InputStream): ImageBridgeHandshakeFrame = json.decodeFromString(LengthPrefixedFrameCodec.readPayload(input))

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

@Serializable
data class ImageBridgeHandshakeFrame(
    val type: String,
    val protocolVersion: Int? = null,
    val clientNonce: String? = null,
    val serverNonce: String? = null,
    val socketName: String? = null,
    val timestampMs: Long? = null,
    val proof: String? = null,
)
