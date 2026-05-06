package party.qwer.iris

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val imageBridgeProtocolJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

interface ImageBridgeProtocolFrameIo {
    fun writeFrame(
        output: OutputStream,
        request: ImageBridgeRequest,
    ) = writeFramePayload(output, imageBridgeProtocolJson.encodeToString(request))

    fun writeFrame(
        output: OutputStream,
        response: ImageBridgeResponse,
    ) = writeFramePayload(output, imageBridgeProtocolJson.encodeToString(response))

    fun readRequestFrame(input: InputStream): ImageBridgeRequest = imageBridgeProtocolJson.decodeFromString(readFramePayload(input))

    fun readResponseFrame(input: InputStream): ImageBridgeResponse = imageBridgeProtocolJson.decodeFromString(readFramePayload(input))
}

private fun writeFramePayload(
    output: OutputStream,
    payload: String,
) = LengthPrefixedFrameCodec.writePayload(output, payload)

private fun readFramePayload(input: InputStream): String = LengthPrefixedFrameCodec.readPayload(input)
