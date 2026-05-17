package party.qwer.iris.imagebridge.runtime.karing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import yP.d

class KaringAotBridgeProviderTest {
    @Test
    fun `payloadJson returns Kakao oauth access token and device id`() {
        val loader = d::class.java.classLoader ?: error("test classloader unavailable")
        val provider = KaringAotBridgeProvider(loader)

        assertTrue(provider.isAvailable())
        d.a.refreshCalls = 0
        d.a.lastCaller = ""

        val payload = JSONObject(provider.payloadJson())
        val aot = payload.getJSONObject("aot")
        assertEquals(1, d.a.refreshCalls)
        assertEquals("iris-karing", d.a.lastCaller)
        assertEquals("access-token", aot.getString("access_token"))
        assertEquals("device-id", aot.getString("d_id"))
        assertEquals("Kakao:https://sharer.kakao.com", payload.getString("ka_tgt"))
    }
}
