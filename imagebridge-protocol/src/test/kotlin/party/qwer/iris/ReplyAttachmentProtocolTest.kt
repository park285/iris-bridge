package party.qwer.iris

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ReplyAttachmentProtocolTest {
    @Test
    fun `builds markdown attachment with mentions and session id`() {
        val attachment =
            assertNotNull(
                ReplyAttachmentProtocol.build(
                    markdown = true,
                    mentionsJson = """{"mentions":[{"user_id":123,"at":[1],"len":3}]}""",
                    sessionId = "req-1",
                ),
            )
        val json = JSONObject(attachment)

        assertEquals("com.kakao.talk", json.getString("callingPkg"))
        assertEquals(true, json.getBoolean("markdown"))
        assertEquals(true, json.getBoolean("f"))
        assertEquals("req-1", json.getString("irisSessionId"))
        assertEquals(1, json.getJSONArray("mentions").length())
    }

    @Test
    fun `returns null when attachment has no metadata`() {
        assertEquals(null, ReplyAttachmentProtocol.build(markdown = false, mentionsJson = null, sessionId = null))
    }

    @Test
    fun `ignores malformed mentions metadata`() {
        val attachment =
            assertNotNull(
                ReplyAttachmentProtocol.build(
                    markdown = false,
                    mentionsJson = "not-json",
                    sessionId = "req-2",
                ),
            )

        assertFalse(JSONObject(attachment).has("mentions"))
    }
}
