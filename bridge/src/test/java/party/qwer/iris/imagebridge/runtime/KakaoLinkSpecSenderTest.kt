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
import party.qwer.iris.imagebridge.runtime.send.extractKakaoLinkAppKey
import party.qwer.iris.imagebridge.runtime.send.hasExplicitKakaoLinkTemplateArgs
import party.qwer.iris.imagebridge.runtime.send.hasResolvedIrisKakaoLinkTemplate
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkAttachmentsMatch
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkDisplayPatchAttachment
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkPendingCleanupAttachmentsMatch
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkSpecCommitVerificationAttachment
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkSpecPatchMatchAttachments
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkSpecSendAttachment
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KakaoLinkSpecSenderTest {
    @Test
    fun `kakaolink commit match requires same template and item titles`() {
        val expected =
            """
            {
              "template_id": "133218",
              "C": {"ITL": [
                {"TD": {"T": "첫 번째"}},
                {"TD": {"T": "두 번째"}}
              ]},
              "K": {"ti": "133218"}
            }
            """.trimIndent()
        val committed =
            """
            {
              "ti": "133218",
              "C": {"ITL": [
                {"TD": {"T": "첫 번째"}},
                {"TD": {"T": "두 번째"}}
              ]}
            }
            """.trimIndent()
        val stale =
            """
            {
              "ti": "133218",
              "C": {"ITL": [
                {"TD": {"T": "다른 방송"}}
              ]}
            }
            """.trimIndent()

        assertTrue(kakaoLinkAttachmentsMatch(expected, committed))
        assertFalse(kakaoLinkAttachmentsMatch(expected, stale))
    }

    @Test
    fun `kakaolink commit match uses template arg item titles for skeletal list`() {
        val expected =
            """
            {
              "template_id": "133218",
              "C": {"ITL": [
                {"L": {"LPC": "https://example.test/1"}, "TH": {"W": 200, "H": 200}},
                {"L": {"LPC": "https://example.test/2"}, "TH": {"W": 200, "H": 200}}
              ]},
              "K": {"ti": "133218"},
              "template_args": {
                "item1_title": "첫 번째",
                "item2_title": "두 번째"
              }
            }
            """.trimIndent()
        val committed =
            """
            {
              "ti": "133218",
              "C": {"ITL": [
                {"TD": {"T": "첫 번째"}},
                {"TD": {"T": "두 번째"}}
              ]}
            }
            """.trimIndent()
        val stale =
            """
            {
              "ti": "133218",
              "C": {"ITL": [
                {"TD": {"T": "다른 방송"}}
              ]}
            }
            """.trimIndent()

        assertTrue(kakaoLinkAttachmentsMatch(expected, committed))
        assertFalse(kakaoLinkAttachmentsMatch(expected, stale))
    }

    @Test
    fun `kakaolink commit match rejects single card template without item title`() {
        val expected =
            """
            {
              "template_id": "133220",
              "C": {"ITL": [
                {"TD": {"T": "단건 방송"}}
              ]},
              "K": {"ti": "133220"}
            }
            """.trimIndent()
        val committed =
            """
            {
              "ti": "133220",
              "C": {"HD": {"TD": {"T": "방송 5분 전 알림"}}}
            }
            """.trimIndent()

        assertFalse(kakaoLinkAttachmentsMatch(expected, committed))
    }

    @Test
    fun `kakaolink pending cleanup match rejects same template with different header`() {
        val expected =
            """
            {
              "template_id": "133266",
              "C": {"HD": {"TD": {"T": "첫 번째 알림"}}},
              "K": {"ti": "133266"},
              "template_args": {"alarm_title": "첫 번째 알림"}
            }
            """.trimIndent()
        val stale =
            """
            {
              "ti": "133266",
              "C": {"HD": {"TD": {"T": "다른 알림"}}},
              "ta": {"alarm_title": "다른 알림"}
            }
            """.trimIndent()

        assertFalse(kakaoLinkPendingCleanupAttachmentsMatch(expected, stale))
    }

    @Test
    fun `kakaolink match rejects same template and header with different web url`() {
        val expected =
            """
            {
              "template_id": "133266",
              "C": {"HD": {"TD": {"T": "동일한 알림"}}},
              "K": {"ti": "133266"},
              "template_args": {
                "alarm_title": "동일한 알림",
                "web_url": "watch?v=expected01"
              }
            }
            """.trimIndent()
        val stale =
            """
            {
              "ti": "133266",
              "C": {"HD": {"TD": {"T": "동일한 알림"}}},
              "ta": {
                "alarm_title": "동일한 알림",
                "web_url": "watch?v=stale00002"
              }
            }
            """.trimIndent()

        assertFalse(kakaoLinkAttachmentsMatch(expected, stale))
        assertFalse(kakaoLinkPendingCleanupAttachmentsMatch(expected, stale))
    }

    @Test
    fun `kakaolink pending cleanup match rejects attachments without template id`() {
        val expected =
            """
            {
              "C": {"HD": {"TD": {"T": "방송 알림"}}},
              "template_args": {"alarm_title": "방송 알림"}
            }
            """.trimIndent()
        val pending =
            """
            {
              "C": {"HD": {"TD": {"T": "방송 알림"}}},
              "ta": {"alarm_title": "방송 알림"}
            }
            """.trimIndent()

        assertFalse(kakaoLinkPendingCleanupAttachmentsMatch(expected, pending))
        assertFalse(kakaoLinkAttachmentsMatch(expected, pending))
    }

    @Test
    fun `kakaolink pending cleanup match accepts same template and item titles`() {
        val expected =
            """
            {
              "template_id": "133266",
              "C": {"HD": {"TD": {"T": "방송 알림"}}, "ITL": [
                {"TD": {"T": "첫 번째"}}
              ]},
              "K": {"ti": "133266"}
            }
            """.trimIndent()
        val pending =
            """
            {
              "ti": "133266",
              "C": {"HD": {"TD": {"T": "방송 알림"}}, "ITL": [
                {"TD": {"T": "첫 번째"}}
              ]}
            }
            """.trimIndent()

        assertTrue(kakaoLinkPendingCleanupAttachmentsMatch(expected, pending))
    }

    @Test
    fun `kakaolink template route helpers use bridge core policy`() {
        assertTrue(
            hasExplicitKakaoLinkTemplateArgs(
                """
                {"templateArgs": {"item1_title": "첫 번째"}}
                """.trimIndent(),
            ),
        )
        assertFalse(hasExplicitKakaoLinkTemplateArgs("not-json"))
        assertTrue(
            hasResolvedIrisKakaoLinkTemplate(
                """
                {"P": {"SID": "iris_133218"}, "C": {"ITL": [{"TD": {"T": "첫 번째"}}]}}
                """.trimIndent(),
            ),
        )
        assertFalse(
            hasResolvedIrisKakaoLinkTemplate(
                """
                {"P": {"SNM": "hololive-bot"}, "C": {"ITL": []}}
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `kakaolink app key extraction uses bridge core policy`() {
        assertEquals(
            "bfbfe8b641716d3f45e01a3b7a03f13d",
            extractKakaoLinkAppKey(
                """
                {"P": {"SL": {"LCA": "kakaobfbfe8b641716d3f45e01a3b7a03f13d://kakaolink"}}}
                """.trimIndent(),
            ),
        )
        assertEquals(
            "46e8bda79095ab1dea785ef1adad5117",
            extractKakaoLinkAppKey("https://example.test/path?app_key=46e8bda79095ab1dea785ef1adad5117"),
        )
        assertEquals(null, extractKakaoLinkAppKey("app_key=46e8bda79095ab1dea785ef1adad5117"))
    }

    @Test
    fun `kakaolink spec send attachment fails closed when bridge core builder is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                kakaoLinkSpecSendAttachment("""{"template_id":"133218"}""") { null }
            }

        assertEquals("bridge core unavailable to build KakaoLinkSpec send attachment", error.message)
    }

    @Test
    fun `reflective sender prefers existing chat id method for server generated template`() {
        FakeKakaoLinkSpecRecorder.clear()
        val sender =
            ReflectiveKakaoLinkSpecSender(
                loader = checkNotNull(KakaoLinkSpecSenderTest::class.java.classLoader),
                logInfo = { _, _ -> },
            )

        val sent =
            sender.send(
                roomId = 18478615493603057L,
                chatRoom = null,
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
    fun `reflective sender keeps existing chat id method for direct chat room`() {
        FakeKakaoLinkSpecRecorder.clear()
        val sender =
            ReflectiveKakaoLinkSpecSender(
                loader = checkNotNull(KakaoLinkSpecSenderTest::class.java.classLoader),
                logInfo = { _, _ -> },
            )

        val sent =
            sender.send(
                roomId = 88006000000001L,
                chatRoom = null,
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
                requestId = "req-karing-direct-room",
            )

        assertTrue(sent)
        assertEquals(listOf("c:88006000000001"), FakeKakaoLinkSpecRecorder.calls)
    }

    @Test
    fun `reflective sender keeps existing chat id method for four item Iris list`() {
        FakeKakaoLinkSpecRecorder.clear()
        val sender =
            ReflectiveKakaoLinkSpecSender(
                loader = checkNotNull(KakaoLinkSpecSenderTest::class.java.classLoader),
                logInfo = { _, _ -> },
            )

        val sent =
            sender.send(
                roomId = 88006000000001L,
                chatRoom = null,
                message = "테스트",
                rawAttachment =
                    """
                    {
                      "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
                      "template_id": "133218",
                      "P": {"VA": "6.0.0", "SID": "iris_133218", "SDID": "133218", "SNM": "hololive-bot"},
                      "C": {
                        "HD": {"TD": {"T": "5분 전 알림 · 4건"}},
                        "ITL": [
                          {"TD": {"T": "첫 번째"}},
                          {"TD": {"T": "두 번째"}},
                          {"TD": {"T": "세 번째"}},
                          {"TD": {"T": "네 번째"}}
                        ]
                      },
                      "K": {"ti": "133218"},
                      "template_args": {"item1_title": "첫 번째", "item4_title": "네 번째"}
                    }
                    """.trimIndent(),
                requestId = "req-karing-four-item-room",
            )

        assertTrue(sent)
        assertEquals(listOf("c:88006000000001"), FakeKakaoLinkSpecRecorder.calls)
    }

    @Test
    fun `reflective sender does not fall back to receiver method when existing chat id method returns false`() {
        FakeKakaoLinkSpecRecorder.clear()
        FakeKakaoLinkSpecRecorder.existingChatIdResult = false
        val sender =
            ReflectiveKakaoLinkSpecSender(
                loader = checkNotNull(KakaoLinkSpecSenderTest::class.java.classLoader),
                logInfo = { _, _ -> },
            )

        val sent =
            sender.send(
                roomId = 18478615493603057L,
                chatRoom = null,
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

        assertFalse(sent)
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
        val templateArgs = JSONObject(params.getValue("template_args"))
        assertEquals("card", templateArgs.getString("alarm_title"))
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
        assertTrue(params.containsKey("template_json"))
        assertEquals("5분 전 알림", templateArgs.getString("alarm_title"))
        assertEquals("테스트 방송", templateArgs.getString("stream_title"))
        assertEquals("watch?v=abc", templateArgs.getString("web_url"))
        assertFalse(templateArgs.has("item1_description"))
    }

    @Test
    fun `keeps resolved Iris template json when explicit query template args exist`() {
        val query =
            buildKakaoLinkV4EncodedQuery(
                """
                {
                  "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
                  "template_id": "133218",
                  "P": {
                    "VA": "6.0.0",
                    "SID": "iris_133218",
                    "SDID": "133218",
                    "SNM": "hololive-bot"
                  },
                  "C": {
                    "HD": {"TD": {"T": "5분 전 알림"}},
                    "ITL": [{"TD": {"T": "테스트 방송", "D": "테스트 멤버 · 21:00"}}]
                  },
                  "K": {"ti": "133218"},
                  "template_args": {"item1_title": "테스트 방송"}
                }
                """.trimIndent(),
            )
        val keys =
            query
                .split("&")
                .map { entry -> URLDecoder.decode(entry.substringBefore("="), "UTF-8") }
                .toSet()

        assertTrue("template_json" in keys)
        assertTrue("template_args" in keys)
    }

    @Test
    fun `uses legacy server template json for single resolved Iris template`() {
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133220",
              "P": {"VA": "6.0.0", "SID": "iris_133220", "SDID": "133220", "SNM": "hololive-bot"},
              "C": {
                "HD": {"TD": {"T": "5분 전 알림"}},
                "ITL": [{"TD": {"T": "단건 방송", "D": "미오 · 05/18 07:30"}}]
              },
              "K": {"ti": "133220"},
              "template_args": {"item1_title": "단건 방송", "visible_stream_count": "1"}
            }
            """.trimIndent()
        val params =
            buildKakaoLinkV4EncodedQuery(kakaoLinkSpecSendAttachment(attachment))
                .split("&")
                .associate { entry ->
                    val parts = entry.split("=", limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                }

        assertEquals("133220", params["template_id"])
        val templateJson = JSONObject(params.getValue("template_json"))
        assertEquals("133220", templateJson.getJSONObject("K").getString("ti"))
        assertFalse(templateJson.getJSONObject("P").has("SID"))
        assertEquals("133220", templateJson.getJSONObject("P").getString("SDID"))
        assertEquals(1, templateJson.getJSONObject("C").getJSONArray("ITL").length())
        assertFalse(templateJson.getJSONObject("C").has("BUL"))
        assertFalse(
            templateJson
                .getJSONObject("C")
                .getJSONArray("ITL")
                .getJSONObject(0)
                .has("L"),
        )
        assertEquals("133220", JSONObject(kakaoLinkSpecCommitVerificationAttachment(attachment)).getJSONObject("K").getString("ti"))
        assertEquals(listOf(JSONObject(attachment).toString()), kakaoLinkSpecPatchMatchAttachments(attachment).map { JSONObject(it).toString() })
    }

    @Test
    fun `uses legacy server template json for four item resolved Iris template`() {
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"VA": "6.0.0", "SID": "iris_133218", "SDID": "133218", "SNM": "hololive-bot"},
              "C": {
                "HD": {"TD": {"T": "5분 전 알림 · 4건"}},
                "ITL": [
                  {"TD": {"T": "첫 번째"}},
                  {"TD": {"T": "두 번째"}},
                  {"TD": {"T": "세 번째"}},
                  {"TD": {"T": "네 번째"}}
                ]
              },
              "K": {"ti": "133218"},
              "template_args": {
                "item1_title": "첫 번째",
                "item2_title": "두 번째",
                "item3_title": "세 번째",
                "item4_title": "네 번째",
                "visible_stream_count": "4",
                "stream_count": "4"
              }
            }
            """.trimIndent()
        val params =
            buildKakaoLinkV4EncodedQuery(kakaoLinkSpecSendAttachment(attachment))
                .split("&")
                .associate { entry ->
                    val parts = entry.split("=", limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                }
        val templateArgs = JSONObject(params.getValue("template_args"))
        val templateJson = JSONObject(params.getValue("template_json"))

        assertEquals("133218", params["template_id"])
        assertEquals("133218", templateJson.getJSONObject("K").getString("ti"))
        assertFalse(templateJson.getJSONObject("P").has("SID"))
        assertEquals("133218", templateJson.getJSONObject("P").getString("SDID"))
        assertEquals(4, templateJson.getJSONObject("C").getJSONArray("ITL").length())
        assertFalse(templateJson.getJSONObject("C").has("BUL"))
        assertFalse(
            templateJson
                .getJSONObject("C")
                .getJSONArray("ITL")
                .getJSONObject(0)
                .has("L"),
        )
        assertEquals("4", templateArgs.getString("visible_stream_count"))
        assertEquals(
            "4",
            JSONObject(kakaoLinkSpecCommitVerificationAttachment(attachment))
                .getJSONObject("C")
                .getJSONArray("ITL")
                .length()
                .toString(),
        )
        val matchAttachments = kakaoLinkSpecPatchMatchAttachments(attachment).map(::JSONObject)
        assertEquals(1, matchAttachments.size)
        assertEquals("133218", matchAttachments.single().getJSONObject("K").getString("ti"))
    }

    @Test
    fun `display patch keeps server envelope while restoring four original items`() {
        val committedCarrier =
            """
            {
              "P": {
                "TP": "List",
                "ME": "5분 전 알림 · 4건",
                "SID": "capri_1369981",
                "DID": "https://www.youtube.com/watch?v=first",
                "SDID": "133222",
                "SNM": "hololive-bot",
                "SST": {
                  "SR": "receiver",
                  "L": {
                    "LPC": "https://apps.kakao.com/talk/message/block?msg_type=share&app_key=bfbfe8b641716d3f45e01a3b7a03f13d&tid=133222&rid=carrier",
                    "LMO": "https://apps.kakao.com/talk/message/block?msg_type=share&app_key=bfbfe8b641716d3f45e01a3b7a03f13d&tid=133222&rid=carrier"
                  }
                },
                "A": {"version": 3},
                "L": {"LPC": "https://www.youtube.com/watch?v=first", "LMO": "https://www.youtube.com/watch?v=first"}
              },
              "C": {
                "HD": {"TD": {"T": "5분 전 알림 · 4건"}},
                "ITL": [
                  {"TD": {"T": "첫 번째"}},
                  {"TD": {"T": "두 번째"}},
                  {"TD": {"T": "세 번째"}}
                ],
                "BUT": 0
              },
              "K": {"ak": "bfbfe8b641716d3f45e01a3b7a03f13d", "av": "6.0.0", "ti": "133222", "lv": "4.0"}
            }
            """.trimIndent()
        val rawOriginal =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"ME": "5분 전 알림 · 4건", "SDID": "133218"},
              "C": {
                "HD": {"TD": {"T": "5분 전 알림 · 4건"}},
                "ITL": [
                  {"TD": {"T": "첫 번째"}, "L": {"LPC": "https://www.youtube.com/watch?v=one", "LMO": "https://www.youtube.com/watch?v=one"}},
                  {"TD": {"T": "두 번째"}, "L": {"LPC": "https://www.youtube.com/watch?v=two", "LMO": "https://www.youtube.com/watch?v=two"}},
                  {"TD": {"T": "세 번째"}, "L": {"LPC": "https://www.youtube.com/watch?v=three", "LMO": "https://www.youtube.com/watch?v=three"}},
                  {"TD": {"T": "네 번째"}, "L": {"LPC": "https://www.youtube.com/watch?v=four", "LMO": "https://www.youtube.com/watch?v=four"}}
                ],
                "BUL": [{"BU": {"T": "더보기"}}]
              },
              "K": {"ti": "133218"},
              "template_args": {"item4_title": "네 번째"}
            }
            """.trimIndent()

        val patched = JSONObject(kakaoLinkDisplayPatchAttachment(committedCarrier, rawOriginal))

        assertEquals("capri_1369981", patched.getJSONObject("P").getString("SID"))
        assertTrue(patched.getJSONObject("P").has("SST"))
        assertTrue(patched.getJSONObject("P").has("A"))
        assertEquals("133218", patched.getJSONObject("P").getString("SDID"))
        assertTrue(
            patched
                .getJSONObject("P")
                .getJSONObject("SST")
                .getJSONObject("L")
                .getString("LPC")
                .contains("tid=133218"),
        )
        assertFalse(
            patched
                .getJSONObject("P")
                .getJSONObject("SST")
                .getJSONObject("L")
                .getString("LPC")
                .contains("tid=133222"),
        )
        assertEquals("133218", patched.getJSONObject("K").getString("ti"))
        assertEquals("4.0", patched.getJSONObject("K").getString("lv"))
        assertFalse(patched.has("template_args"))
        assertFalse(patched.has("template_id"))
        assertEquals(4, patched.getJSONObject("C").getJSONArray("ITL").length())
        assertEquals(0, patched.getJSONObject("C").getInt("BUT"))
        assertFalse(patched.getJSONObject("C").has("BUL"))
        assertEquals(
            "네 번째",
            patched
                .getJSONObject("C")
                .getJSONArray("ITL")
                .getJSONObject(3)
                .getJSONObject("TD")
                .getString("T"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=one",
            patched.getJSONObject("P").getJSONObject("L").getString("LPC"),
        )
    }

    @Test
    fun `display patch attachment fails closed when bridge core patcher is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                kakaoLinkDisplayPatchAttachment(
                    committedAttachment = """{"K":{"ti":"133222"}}""",
                    rawAttachment = """{"template_id":"133218"}""",
                ) { _, _ -> null }
            }

        assertEquals("bridge core unavailable to patch KakaoLink display attachment", error.message)
    }

    @Test
    fun `infers hololive list template args from resolved attachment`() {
        val query =
            buildKakaoLinkV4EncodedQuery(
                """
                {
                  "P": {
                    "VA": "6.0.0",
                    "SDID": "133220",
                    "SL": {
                      "LCA": "kakaobfbfe8b641716d3f45e01a3b7a03f13d://kakaolink"
                    }
                  },
                  "C": {
                    "HD": {"TD": {"T": "쇼츠 알림 - 테스트 제목"}},
                    "ITL": [{
                      "TD": {"T": "테스트 제목", "D": "카푸 · 쇼츠"},
                      "TH": {"THU": "https://i.ytimg.com/vi/abc/maxresdefault.jpg"},
                      "L": {
                        "LPC": "https://www.youtube.com/shorts/abc",
                        "LMO": "https://www.youtube.com/shorts/abc"
                      }
                    }]
                  },
                  "K": {"ti": "133220"}
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

        assertEquals("쇼츠 알림 - 테스트 제목", templateArgs.getString("alarm_title"))
        assertEquals("테스트 제목", templateArgs.getString("title"))
        assertEquals("테스트 제목", templateArgs.getString("stream_title"))
        assertEquals("테스트 제목", templateArgs.getString("item1_title"))
        assertEquals("카푸 · 쇼츠", templateArgs.getString("item1_desc"))
        assertEquals("카푸 · 쇼츠", templateArgs.getString("stream_desc"))
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", templateArgs.getString("item1_thumbnail"))
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", templateArgs.getString("thumbnail"))
        assertEquals("https://www.youtube.com/shorts/abc", templateArgs.getString("item1_full_web_url"))
        assertEquals("https://www.youtube.com/shorts/abc", templateArgs.getString("web_url"))
        assertEquals("1", templateArgs.getString("visible_stream_count"))
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
    fun `reply leverage attachment merge fails closed when bridge core merger is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                mergeLeverageAttachment(
                    generatedAttachment = """{"P":{"A":{"code":"signed"}}}""",
                    rawAttachment = """{"K":{"ti":"121065"}}""",
                ) { _, _ -> null }
            }

        assertEquals("bridge core unavailable to merge reply leverage attachment", error.message)
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
        val encrypted =
            KakaoChatLogAttachmentCrypto.encrypt(
                encType = 31,
                plaintext = "test",
                userId = 438562408L,
            )

        assertEquals("WXFmkb1MZ8akXwAOS8BeOQ==", encrypted)
        assertEquals(
            "test",
            KakaoChatLogAttachmentCrypto.decrypt(
                encType = 31,
                ciphertext = encrypted,
                userId = 438562408L,
            ),
        )
    }
}
