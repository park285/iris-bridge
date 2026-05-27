package party.qwer.iris

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageBridgeMuxProtocolTest {
    @Test
    fun `request frame roundtrips`() {
        val frame =
            ImageBridgeMuxFrame(
                type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                correlationId = "mux-1",
                request = ImageBridgeProtocol.buildHealthRequest(token = "bridge-token"),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeMuxProtocol.writeFrame(buffer, frame)

        val restored = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_REQUEST, restored.type)
        assertEquals(ImageBridgeMuxProtocol.MUX_VERSION, restored.muxVersion)
        assertEquals("mux-1", restored.correlationId)
        assertEquals(ImageBridgeProtocol.ACTION_HEALTH, restored.request?.action)
        assertEquals("bridge-token", restored.request?.token)
    }

    @Test
    fun `response frame roundtrips`() {
        val frame =
            ImageBridgeMuxFrame(
                type = ImageBridgeMuxProtocol.TYPE_RESPONSE,
                correlationId = "mux-2",
                response = ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK),
            )
        val buffer = ByteArrayOutputStream()

        ImageBridgeMuxProtocol.writeFrame(buffer, frame)

        val restored = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_RESPONSE, restored.type)
        assertEquals("mux-2", restored.correlationId)
        assertEquals(ImageBridgeProtocol.STATUS_OK, restored.response?.status)
    }

    @Test
    fun `unknown fields are ignored`() {
        val payload =
            """
            {"type":"ping","muxVersion":2,"correlationId":"mux-3","futureField":"ignored"}
            """.trimIndent()
        val buffer = ByteArrayOutputStream()
        LengthPrefixedFrameCodec.writePayload(buffer, payload)

        val restored = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(buffer.toByteArray()))

        assertEquals(ImageBridgeMuxProtocol.TYPE_PING, restored.type)
        assertEquals("mux-3", restored.correlationId)
    }

    @Test
    fun `frame size over limit is rejected`() {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).writeInt(LengthPrefixedFrameCodec.MAX_FRAME_SIZE + 1)

        assertFailsWith<IllegalArgumentException> {
            ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(buffer.toByteArray()))
        }
    }

    @Test
    fun `malformed JSON is rejected`() {
        val buffer = ByteArrayOutputStream()
        LengthPrefixedFrameCodec.writePayload(buffer, "{")

        assertFailsWith<Exception> {
            ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(buffer.toByteArray()))
        }
    }

    @Test
    fun `payload bytes writer preserves big endian length prefix`() {
        val bytes = "payload".toByteArray(Charsets.UTF_8)
        val buffer = ByteArrayOutputStream()

        LengthPrefixedFrameCodec.writePayloadBytes(buffer, bytes)

        val framed = buffer.toByteArray()
        assertEquals(bytes.size, ByteBuffer.wrap(framed, 0, Int.SIZE_BYTES).int)
        assertContentEquals(bytes, framed.copyOfRange(Int.SIZE_BYTES, framed.size))
    }

    @Test
    fun `preencoded mux frame bytes preserve wire shape`() {
        val frame =
            ImageBridgeMuxFrame(
                type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                correlationId = "mux-preencoded",
                request = ImageBridgeProtocol.buildHealthRequest(token = "bridge-token"),
            )
        val encoded = ImageBridgeMuxProtocol.encodeFrameBytes(frame)
        val encodedJson = encoded.toString(Charsets.UTF_8)
        val buffer = ByteArrayOutputStream()

        ImageBridgeMuxProtocol.writeFrameBytes(buffer, encoded)

        assertTrue(encodedJson.contains(""""muxVersion":${ImageBridgeMuxProtocol.MUX_VERSION}"""))
        assertFalse(encodedJson.contains(""""response":null"""))
        val restored = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(frame.type, restored.type)
        assertEquals(frame.correlationId, restored.correlationId)
        assertEquals(ImageBridgeProtocol.ACTION_HEALTH, restored.request?.action)
    }
}
