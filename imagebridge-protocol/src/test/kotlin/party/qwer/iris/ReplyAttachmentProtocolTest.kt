package party.qwer.iris

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ReplyAttachmentProtocolTest {
    @Test
    fun `builds markdown attachment with mentions without private session metadata`() {
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
        assertFalse(json.has("irisSessionId"))
        assertEquals(1, json.getJSONArray("mentions").length())
    }

    @Test
    fun `builds plain text mention attachment like Kakao native mention metadata`() {
        val attachment =
            assertNotNull(
                ReplyAttachmentProtocol.build(
                    markdown = false,
                    mentionsJson = """{"mentions":[{"user_id":123,"at":[1],"len":3}]}""",
                    sessionId = "req-mention",
                ),
            )
        val json = JSONObject(attachment)

        assertFalse(json.has("callingPkg"))
        assertEquals(1, json.getJSONArray("mentions").length())
        assertEquals(123L, json.getJSONArray("mentions").getJSONObject(0).getLong("user_id"))
    }

    @Test
    fun `returns null when attachment has no metadata`() {
        assertEquals(null, ReplyAttachmentProtocol.build(markdown = false, mentionsJson = null, sessionId = null))
    }

    @Test
    fun `returns null when only session id is present`() {
        assertEquals(null, ReplyAttachmentProtocol.build(markdown = false, mentionsJson = null, sessionId = "req-plain"))
    }

    @Test
    fun `ignores malformed mentions metadata`() {
        assertEquals(
            null,
            ReplyAttachmentProtocol.build(
                markdown = false,
                mentionsJson = "not-json",
                sessionId = "req-2",
            ),
        )
    }
}
