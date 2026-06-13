package party.qwer.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object ImageBridgeHandshakeProtocol {
    const val TYPE_HELLO = "hello"
    const val TYPE_SERVER_PROOF = "server_proof"
    const val TYPE_CLIENT_PROOF = "client_proof"
    const val AUTHENTICATION_FAILED = "bridge authentication failed"

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

    fun writeFrame(
        output: OutputStream,
        frame: ImageBridgeHandshakeFrame,
    ) = LengthPrefixedFrameCodec.writePayload(output, json.encodeToString(frame))

    fun readFrame(input: InputStream): ImageBridgeHandshakeFrame = json.decodeFromString(LengthPrefixedFrameCodec.readPayload(input))
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
