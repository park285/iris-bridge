@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import com.kakao.talk.model.kakaolink.FakeKakaoLinkSpecRecorder
import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.send.KakaoChatLogAttachmentCrypto
import party.qwer.iris.imagebridge.runtime.send.ReflectiveKakaoLinkSpecSender
import party.qwer.iris.imagebridge.runtime.send.buildKakaoLinkV4EncodedQuery
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KakaoLinkSpecSenderTest {
    @Test
    fun `reflective sender prefers existing chat id method over receiver method`() {
        FakeKakaoLinkSpecRecorder.clear()
        val sender =
            ReflectiveKakaoLinkSpecSender(
                loader = checkNotNull(KakaoLinkSpecSenderTest::class.java.classLoader),
                listener = null,
                logInfo = { _, _ -> },
            )

        val sent =
            sender.send(
                roomId = 18478615493603057L,
                message = "테스트",
                rawAttachment =
                    """
                    {
                      "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
                      "template_id": "133220",
                      "P": {"VA": "6.0.0", "SDID": "133220"},
                      "C": {"HD": {"TD": {"T": "테스트"}}},
                      "K": {"ti": "133220"},
                      "template_args": {"title": "테스트"}
                    }
                    """.trimIndent(),
                requestId = "req-karing-target-room",
            )

        assertTrue(sent)
        assertEquals(listOf("c:18478615493603057"), FakeKakaoLinkSpecRecorder.calls)
    }

    @Test
    fun `builds KakaoLink V4 query from raw card attachment`() {
        val query =
            buildKakaoLinkV4EncodedQuery(
                """
                {
                  "P": {
                    "VA": "6.0.0",
                    "SDID": "121065",
                    "SL": {
                      "LCA": "kakao46e8bda79095ab1dea785ef1adad5117://kakaolink"
                    }
                  },
                  "C": {"HD": {"TD": {"T": "card"}}},
                  "K": {"ti": "121065"}
                }
                """.trimIndent(),
            )

        val params =
            query
                .split("&")
                .associate { entry ->
                    val parts = entry.split("=", limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                }

        assertEquals("4.0", params["linkver"])
        assertEquals("6.0.0", params["appver"])
        assertEquals("46e8bda79095ab1dea785ef1adad5117", params["appkey"])
        assertEquals("121065", params["template_id"])
        val title =
            JSONObject(params.getValue("template_json"))
                .getJSONObject("C")
                .getJSONObject("HD")
                .getJSONObject("TD")
                .getString("T")
        assertEquals("card", title)
    }

    @Test
    fun `builds KakaoLink V4 query with explicit template args`() {
        val query =
            buildKakaoLinkV4EncodedQuery(
                """
                {
                  "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
                  "template_id": "133218",
                  "P": {
                    "VA": "6.0.0",
                    "SDID": "133218",
                    "SL": {
                      "LCA": "kakaobfbfe8b641716d3f45e01a3b7a03f13d://kakaolink"
                    }
                  },
                  "C": {
                    "HD": {"TD": {"T": "5분 전 알림"}},
                    "ITL": [{"TD": {"T": "테스트 방송", "D": "테스트 멤버 · 21:00 · 예정"}}]
                  },
                  "K": {"ti": "133218"},
                  "template_args": {
                    "alarm_title": "5분 전 알림",
                    "stream_title": "테스트 방송",
                    "member_name": "테스트 멤버",
                    "start_at": "21:00",
                    "status": "예정",
                    "thumbnail": "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
                    "web_url": "watch?v=abc",
                    "mobile_web_url": "watch?v=abc"
                  }
                }
                """.trimIndent(),
            )

        val params =
            query
                .split("&")
                .associate { entry ->
                    val parts = entry.split("=", limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                }
        val templateArgs = JSONObject(params.getValue("template_args"))

        assertEquals("bfbfe8b641716d3f45e01a3b7a03f13d", params["appkey"])
        assertEquals("133218", params["template_id"])
        assertFalse(params.containsKey("template_json"))
        assertEquals("5분 전 알림", templateArgs.getString("alarm_title"))
        assertEquals("테스트 방송", templateArgs.getString("stream_title"))
        assertEquals("watch?v=abc", templateArgs.getString("web_url"))
        assertFalse(templateArgs.has("item1_description"))
    }

    @Test
    fun `merges raw KakaoLink content into generated leverage attachment`() {
        val merged =
            mergeLeverageAttachment(
                generatedAttachment =
                    """
                    {
                      "P": {"A": {"code": "signed"}, "RF": "out-client"},
                      "C": {"ITL": [{"TD": {"T": "CPU 사용량"}}]},
                      "K": {"ak": "app", "av": "6.0.0", "ti": "121065", "lv": "4.0"}
                    }
                    """.trimIndent(),
                rawAttachment =
                    """
                    {
                      "P": {"RF": "chat_ln"},
                      "C": {"ITL": [{"TD": {"T": "CPU 사용량", "D": "지금은 한가해요"}}]},
                      "K": {"ti": "121065", "ai": "377386"}
                    }
                    """.trimIndent(),
            )
        val json = JSONObject(merged)

        assertEquals("signed", json.getJSONObject("P").getJSONObject("A").getString("code"))
        assertEquals("out-client", json.getJSONObject("P").getString("RF"))
        assertEquals(
            "지금은 한가해요",
            json
                .getJSONObject("C")
                .getJSONArray("ITL")
                .getJSONObject(0)
                .getJSONObject("TD")
                .getString("D"),
        )
        assertEquals("377386", json.getJSONObject("K").getString("ai"))
        assertEquals("4.0", json.getJSONObject("K").getString("lv"))
    }

    @Test
    fun `reply leverage hook injects attachment and switches write type to connect`() {
        val leverageContexts = ReplyLeveragePendingContextStore()
        leverageContexts.remember(
            ReplyLeveragePendingContext(
                roomId = 123L,
                messageText = "card",
                attachmentText =
                    """
                    {
                      "P": {"TP": "List"},
                      "C": {"ITL": [{"TD": {"T": "CPU 사용량", "D": "지금은 한가해요"}}]},
                      "K": {"ti": "121065"}
                    }
                    """.trimIndent(),
                threadId = 55L,
                threadScope = 3,
                sessionId = "req-card",
                createdAtEpochMs = 1L,
            ),
        )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val sendingLogBuilder =
            FakeTextSendingLog.b(
                chatRoom,
                FakeMessageType.Text,
                0,
                null,
            )
        val sendingLog =
            sendingLogBuilder
                .j("card")
                .c(JSONObject("""{"sessionId":"req-card"}"""))
                .b()
        val args = arrayOf<Any?>(chatRoom, sendingLog, null, null, false)

        handleReplyMarkdownRequestArgs(
            args = args,
            tag = "test",
            markdownPendingContexts = ReplyMarkdownPendingContextStore(),
            mentionPendingContexts = ReplyMentionPendingContextStore(),
            leveragePendingContexts = leverageContexts,
            leverageMessageType = FakeMessageType.Leverage,
            leverageWriteType = FakeWriteType.Connect,
        )

        assertEquals(FakeMessageType.Leverage, sendingLog.messageType)
        assertEquals(FakeWriteType.Connect, args[2])
        assertEquals(55L, sendingLog.V0)
        assertEquals(3, sendingLog.Z)
        val attachment = JSONObject(assertNotNull(sendingLog.G))
        assertEquals(
            "지금은 한가해요",
            attachment
                .getJSONObject("C")
                .getJSONArray("ITL")
                .getJSONObject(0)
                .getJSONObject("TD")
                .getString("D"),
        )
    }

    @Test
    fun `reply leverage commit hook patches generated chat log attachment`() {
        val leverageContexts = ReplyLeveragePendingContextStore()
        leverageContexts.remember(
            ReplyLeveragePendingContext(
                roomId = 123L,
                messageText = "card",
                attachmentText =
                    """
                    {
                      "P": {"TP": "List"},
                      "C": {"ITL": [{"TD": {"T": "CPU 사용량", "D": "지금은 한가해요"}}]},
                      "K": {"ti": "121065"}
                    }
                    """.trimIndent(),
                threadId = 55L,
                threadScope = 3,
                sessionId = "req-card",
                createdAtEpochMs = 1L,
            ),
        )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val sendingLog =
            FakeTextSendingLog
                .b(chatRoom, FakeMessageType.Leverage, 0, null)
                .j("card")
                .c(JSONObject("""{"sessionId":"req-card"}"""))
                .b()
        val request = FakeChatSendingLogRequest(sendingLog)
        val chatLog =
            FakeChatLog(
                roomId = 123L,
                attachment =
                    JSONObject(
                        """
                        {
                          "P": {"A": {"code": "signed"}, "RF": "out-client"},
                          "C": {"ITL": [{"TD": {"T": "CPU 사용량"}}]},
                          "K": {"ak": "app", "av": "6.0.0", "ti": "121065", "lv": "4.0"}
                        }
                        """.trimIndent(),
                    ),
            )

        handleReplyLeverageChatLogCommitArgs(
            request = request,
            args = arrayOf(chatLog),
            tag = "test",
            leveragePendingContexts = leverageContexts,
        )

        val patched = JSONObject(chatLog.attachmentText)
        assertEquals("signed", patched.getJSONObject("P").getJSONObject("A").getString("code"))
        assertEquals(
            "지금은 한가해요",
            patched
                .getJSONObject("C")
                .getJSONArray("ITL")
                .getJSONObject(0)
                .getJSONObject("TD")
                .getString("D"),
        )
    }

    @Test
    fun `encrypts Kakao attachment with chat log key derivation`() {
        val encrypted = KakaoChatLogAttachmentCrypto.encrypt(encType = 31, plaintext = "test", userId = 438562408L)

        assertEquals("WXFmkb1MZ8akXwAOS8BeOQ==", encrypted)
        assertEquals("test", KakaoChatLogAttachmentCrypto.decrypt(encType = 31, ciphertext = encrypted, userId = 438562408L))
    }
}
