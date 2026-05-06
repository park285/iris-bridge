package party.qwer.iris

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
