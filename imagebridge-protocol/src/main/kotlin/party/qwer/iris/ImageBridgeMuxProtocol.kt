package party.qwer.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.qwer.iris.generated.GeneratedBridgeProtocolContract
import java.io.InputStream
import java.io.OutputStream

object ImageBridgeMuxProtocol {
    const val MUX_VERSION = GeneratedBridgeProtocolContract.MUX_VERSION
    const val DEFAULT_SOCKET_NAME = GeneratedBridgeProtocolContract.DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME
    const val TYPE_REQUEST = GeneratedBridgeProtocolContract.MUX_FRAME_TYPE_REQUEST
    const val TYPE_RESPONSE = GeneratedBridgeProtocolContract.MUX_FRAME_TYPE_RESPONSE
    const val TYPE_PING = GeneratedBridgeProtocolContract.MUX_FRAME_TYPE_PING
    const val TYPE_PONG = GeneratedBridgeProtocolContract.MUX_FRAME_TYPE_PONG
    const val TYPE_CANCEL = GeneratedBridgeProtocolContract.MUX_FRAME_TYPE_CANCEL
    const val TYPE_GOAWAY = GeneratedBridgeProtocolContract.MUX_FRAME_TYPE_GOAWAY

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    fun writeFrame(
        output: OutputStream,
        frame: ImageBridgeMuxFrame,
    ) = writeFrameBytes(output, encodeFrameBytes(frame))

    fun encodeFrameBytes(frame: ImageBridgeMuxFrame): ByteArray = json.encodeToString(frame).toByteArray(Charsets.UTF_8)

    fun writeFrameBytes(
        output: OutputStream,
        frameBytes: ByteArray,
    ) = LengthPrefixedFrameCodec.writePayloadBytes(output, frameBytes)

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
