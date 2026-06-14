@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONObject
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeMuxFrameSummaryTest {
    @Test
    fun `mux summary JSON escapes string fields and preserves request marker shape`() {
        val summary =
            ImageBridgeMuxFrame(
                type = "request",
                muxVersion = 2,
                correlationId = "a\"b",
                request =
                    ImageBridgeProtocol.ImageBridgeRequest(
                        action = ImageBridgeProtocol.ACTION_HEALTH,
                        protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                    ),
            ).muxSummaryJson()

        val parsed = JSONObject(summary)

        assertEquals("request", parsed.getString("type"))
        assertEquals(2, parsed.getInt("muxVersion"))
        assertEquals("a\"b", parsed.getString("correlationId"))
        assertEquals(0, parsed.getJSONObject("request").length())
        assertTrue(parsed.has("request"))
        assertFalse(parsed.has("response"))
        assertFalse(parsed.has("errorCode"))
    }
}
