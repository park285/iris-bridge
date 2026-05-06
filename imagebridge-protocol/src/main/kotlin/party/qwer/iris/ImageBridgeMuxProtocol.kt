package party.qwer.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object ImageBridgeMuxProtocol {
    const val MUX_VERSION = 2
    const val DEFAULT_SOCKET_NAME = IrisRuntimePathPolicy.DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME
    const val TYPE_REQUEST = "request"
    const val TYPE_RESPONSE = "response"
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"
    const val TYPE_CANCEL = "cancel"
    const val TYPE_GOAWAY = "goaway"

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    fun writeFrame(
        output: OutputStream,
        frame: ImageBridgeMuxFrame,
    ) = LengthPrefixedFrameCodec.writePayload(output, json.encodeToString(frame))

    fun readFrame(input: InputStream): ImageBridgeMuxFrame = json.decodeFromString(LengthPrefixedFrameCodec.readPayload(input))
}

@Serializable
data class ImageBridgeMuxFrame(
    val type: String,
    val muxVersion: Int = ImageBridgeMuxProtocol.MUX_VERSION,
    val correlationId: String? = null,
    val request: ImageBridgeProtocol.ImageBridgeRequest? = null,
    val response: ImageBridgeProtocol.ImageBridgeResponse? = null,
    val error: String? = null,
    val errorCode: String? = null,
)
