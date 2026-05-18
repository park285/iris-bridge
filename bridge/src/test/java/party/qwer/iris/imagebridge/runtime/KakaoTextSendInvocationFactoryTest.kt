@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.app.Application
import com.kakao.talk.manager.ShareManager
import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.discovery.currentBridgeCapabilities
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendInvocationFactory
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkSpecCommitVerificationAttachment
import party.qwer.iris.imagebridge.runtime.send.kakaoLinkSpecSendAttachment
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KakaoTextSendInvocationFactoryTest {
    @Test
    fun `factory records mention context and resolves ShareManager text path by signature`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val mentionContexts = ReplyMentionPendingContextStore()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                mentionPendingContexts = mentionContexts,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "@alice hello",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = """{"mentions":[{"user_id":"text-alice","at":[1],"len":5}]}""",
            attachmentJson = null,
            requestId = "req-mention",
        )

        assertEquals(chatRoom, ShareManager.chatRoom)
        assertEquals("@alice hello", ShareManager.message)
        assertEquals(false, ShareManager.flag)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
        val pending = assertNotNull(mentionContexts.match(123L, "@alice hello", "req-mention"))
        val mention = JSONObject(pending.attachmentText).getJSONArray("mentions").getJSONObject(0)
        assertEquals("text-alice", mention.getString("user_id"))
        assertEquals("req-mention", pending.sessionId)
    }

    @Test
    fun `factory builds text sending log and invokes request method`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))

        val capability = factory.capability()
        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "@alice hello",
            markdown = true,
            threadId = 55L,
            threadScope = 3,
            mentionsJson = """{"mentions":[{"user_id":7}]}""",
            attachmentJson = null,
            requestId = "req-text",
        )

        assertTrue(capability.ready)
        assertEquals(chatRoom, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertFalse(FakeTextRequestRecorder.shouldRetry)
        assertEquals(null, FakeTextRequestRecorder.listener)
        val sendingLog = FakeTextRequestRecorder.sendingLog as FakeTextSendingLog
        assertEquals(123L, sendingLog.getChatRoomId())
        assertEquals("@alice hello", sendingLog.f0())
        assertEquals("com.kakao.talk.manager.ShareManager", sendingLog.originClass?.name)
        assertEquals("FM", sendingLog.originTag)
        assertEquals(55L, sendingLog.V0)
        assertEquals(3, sendingLog.Z)
        val attachment = JSONObject(requireNotNull(sendingLog.G))
        assertTrue(attachment.getBoolean("markdown"))
        assertFalse(attachment.has("irisSessionId"))
        assertEquals(1, attachment.getJSONArray("mentions").length())
    }

    @Test
    fun `factory sends raw attachment through KakaoLinkSpec first`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val leverageContexts = ReplyLeveragePendingContextStore()
        val leverageCommitContexts = ReplyLeveragePendingContextStore()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                leveragePendingContexts = leverageContexts,
                leverageCommitPendingContexts = leverageCommitContexts,
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))

        val capability = factory.capability()
        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "@alice hello",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            requestId = "req-text",
            attachmentJson = """{"P":{"TP":"List"},"K":{"ti":"121065"}}""",
        )

        assertTrue(capability.ready)
        assertEquals(123L, linkSender.roomId)
        assertEquals("@alice hello", linkSender.message)
        assertEquals("""{"P":{"TP":"List"},"K":{"ti":"121065"}}""", linkSender.rawAttachment)
        assertEquals("req-text", linkSender.requestId)
        assertEquals(123L, patcher.roomId)
        assertEquals("@alice hello", patcher.message)
        assertEquals("""{"P":{"TP":"List"},"K":{"ti":"121065"}}""", patcher.rawAttachment)
        assertEquals("req-text", patcher.requestId)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, ShareManager.message)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
        assertEquals(null, leverageContexts.match(123L, "@alice hello", "req-text"))
        val pending = assertNotNull(leverageCommitContexts.match(123L, "@alice hello", "req-text"))
        assertEquals(null, pending.threadId)
        assertEquals(null, pending.threadScope)
        val rawAttachment = JSONObject(pending.attachmentText)
        assertEquals("List", rawAttachment.getJSONObject("P").getString("TP"))
        assertEquals("121065", rawAttachment.getJSONObject("K").getString("ti"))
    }

    @Test
    fun `factory sends resolved Iris content list with explicit args through KakaoLinkSpec path`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val leverageContexts = ReplyLeveragePendingContextStore()
        val leverageCommitContexts = ReplyLeveragePendingContextStore()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = true)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                leveragePendingContexts = leverageContexts,
                leverageCommitPendingContexts = leverageCommitContexts,
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "P": {"TP": "List", "SID": "iris_133220", "SNM": "hololive-bot"},
              "C": {
                "HD": {"TD": {"T": "방송 5분 전 알림"}},
                "ITL": [{"TD": {"T": "테스트 방송", "D": "미오 · 05/18 07:30"}}]
              },
              "K": {"ti": "133220"},
              "ta": {"item1_title": "테스트 방송"}
            }
            """.trimIndent()

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "방송 5분 전 알림",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            requestId = "req-iris-list",
            attachmentJson = attachment,
        )

        assertEquals(123L, linkSender.roomId)
        assertEquals("방송 5분 전 알림", linkSender.message)
        assertEquals(kakaoLinkSpecSendAttachment(attachment), linkSender.rawAttachment)
        assertEquals("req-iris-list", linkSender.requestId)
        assertEquals(123L, commitVerifier.roomId)
        assertEquals("방송 5분 전 알림", commitVerifier.message)
        assertEquals(123L, commitVerifier.latestRowRoomId)
        assertEquals(900L, commitVerifier.minimumRowId)
        assertEquals("req-iris-list", commitVerifier.requestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.rawAttachment)
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-iris-list", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(null, patcher.roomId)
        assertEquals(null, patcher.message)
        assertEquals(null, patcher.rawAttachment)
        assertEquals(null, patcher.requestId)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, ShareManager.message)
        assertEquals(null, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
        assertEquals(null, leverageCommitContexts.match(123L, "방송 5분 전 알림", "req-iris-list"))
        assertEquals(null, leverageContexts.match(123L, "방송 5분 전 알림", "req-iris-list"))
    }

    @Test
    fun `factory skips KakaoLinkSpec path for threaded raw attachment reply`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val leverageContexts = ReplyLeveragePendingContextStore()
        val leverageCommitContexts = ReplyLeveragePendingContextStore()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                leveragePendingContexts = leverageContexts,
                leverageCommitPendingContexts = leverageCommitContexts,
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "thread card",
            markdown = false,
            threadId = 55L,
            threadScope = 3,
            mentionsJson = null,
            requestId = "req-thread-card",
            attachmentJson = """{"P":{"TP":"List"},"K":{"ti":"121065"}}""",
        )

        assertEquals(null, linkSender.roomId)
        assertEquals(null, patcher.roomId)
        assertEquals(chatRoom, ShareManager.chatRoom)
        assertEquals("thread card", ShareManager.message)
        assertEquals(null, leverageCommitContexts.match(123L, "thread card", "req-thread-card"))
        val pending = assertNotNull(leverageContexts.match(123L, "thread card", "req-thread-card"))
        assertEquals(55L, pending.threadId)
        assertEquals(3, pending.threadScope)
    }

    @Test
    fun `factory sends server generated custom template through committed spec path`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val leverageCommitContexts = ReplyLeveragePendingContextStore()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = true)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                leverageCommitPendingContexts = leverageCommitContexts,
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"TP": "List", "SDID": "133218"},
              "C": {"HD": {"TD": {"T": "5분 전 알림"}}},
              "K": {"ti": "133218"},
              "template_args": {
                "alarm_title": "5분 전 알림",
                "stream_title": "테스트 방송",
                "web_url": "watch?v=abc"
              }
            }
            """.trimIndent()

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "5분 전 알림",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            requestId = "req-template",
            attachmentJson = attachment,
        )

        assertEquals(123L, linkSender.roomId)
        assertEquals("5분 전 알림", linkSender.message)
        assertEquals(kakaoLinkSpecSendAttachment(attachment), linkSender.rawAttachment)
        assertEquals("req-template", linkSender.requestId)
        assertEquals(null, patcher.roomId)
        assertEquals(null, patcher.message)
        assertEquals(null, patcher.rawAttachment)
        assertEquals(null, patcher.requestId)
        assertEquals(123L, commitVerifier.roomId)
        assertEquals("5분 전 알림", commitVerifier.message)
        assertEquals(123L, commitVerifier.latestRowRoomId)
        assertEquals(900L, commitVerifier.minimumRowId)
        assertEquals("req-template", commitVerifier.requestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.rawAttachment)
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-template", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(null, leverageCommitContexts.match(123L, "5분 전 알림", "req-template"))
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, ShareManager.message)
        assertEquals(null, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory treats server generated custom template as sent when pending cleanup fails after commit`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = true, cleanupResult = false)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"TP": "List", "SDID": "133218"},
              "C": {"HD": {"TD": {"T": "5분 전 알림"}}},
              "K": {"ti": "133218"},
              "template_args": {
                "alarm_title": "5분 전 알림",
                "stream_title": "테스트 방송",
                "web_url": "watch?v=abc"
              }
            }
            """.trimIndent()

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "5분 전 알림",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            requestId = "req-template-cleanup-fail",
            attachmentJson = attachment,
        )

        assertEquals(1, linkSender.sendCalls)
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-template-cleanup-fail", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(null, patcher.roomId)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory sends resolved Iris template through KakaoLinkSpec path`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = true)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133220",
              "P": {"TP": "List", "SID": "iris_133220", "SDID": "133220", "SNM": "hololive-bot"},
              "C": {
                "HD": {"TD": {"T": "5분 전 알림"}},
                "ITL": [{"TD": {"T": "테스트 방송", "D": "미오 · 05/18 07:30"}}]
              },
              "K": {"ti": "133220"},
              "template_args": {
                "alarm_title": "5분 전 알림",
                "item1_title": "테스트 방송",
                "item1_desc": "미오 · 05/18 07:30"
              }
            }
            """.trimIndent()

        factory.send(
            roomId = 123L,
            chatRoom = chatRoom,
            message = "5분 전 알림",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            requestId = "req-template",
            attachmentJson = attachment,
        )

        assertEquals(123L, linkSender.roomId)
        assertEquals("5분 전 알림", linkSender.message)
        assertEquals(kakaoLinkSpecSendAttachment(attachment), linkSender.rawAttachment)
        assertEquals("req-template", linkSender.requestId)
        assertEquals(1, linkSender.sendCalls)
        assertEquals(123L, commitVerifier.roomId)
        assertEquals(123L, commitVerifier.latestRowRoomId)
        assertEquals(900L, commitVerifier.minimumRowId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.rawAttachment)
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-template", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(null, patcher.roomId)
        assertEquals(null, patcher.rawAttachment)
        assertEquals(null, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory does not retry resolved Iris template when first spec commit is not observed`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = false, true)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133220",
              "P": {"TP": "List", "SID": "iris_133220", "SDID": "133220", "SNM": "hololive-bot"},
              "C": {
                "HD": {"TD": {"T": "커뮤니티 알림"}},
                "ITL": [{"TD": {"T": "새 커뮤니티", "D": "호로아나 · 05/18 15:22"}}]
              },
              "K": {"ti": "133220"},
              "template_args": {
                "thumbnail": "https://example.com/thumb.jpg",
                "item_title": "새 커뮤니티",
                "item_web_url": "post/abc",
                "alarm_title": "커뮤니티 알림",
                "item_desc": "호로아나 · 05/18 15:22"
              }
            }
            """.trimIndent()

        val error =
            runCatching {
                factory.send(
                    roomId = 123L,
                    chatRoom = chatRoom,
                    message = "커뮤니티 알림",
                    markdown = false,
                    threadId = null,
                    threadScope = null,
                    mentionsJson = null,
                    requestId = "req-retry",
                    attachmentJson = attachment,
                )
            }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error.message?.contains("did not create chat log") == true)
        assertEquals(1, linkSender.sendCalls)
        assertEquals(
            listOf(kakaoLinkSpecSendAttachment(attachment)),
            linkSender.rawAttachments,
        )
        assertEquals(
            listOf<String?>(kakaoLinkSpecCommitVerificationAttachment(attachment)),
            commitVerifier.rawAttachments,
        )
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-retry", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(null, patcher.roomId)
        assertEquals(null, patcher.rawAttachment)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory fails resolved Iris template without direct leverage fallback when spec commit is not observed`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val leverageContexts = ReplyLeveragePendingContextStore()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = false)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                leveragePendingContexts = leverageContexts,
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"TP": "List", "SID": "iris_133218", "SDID": "133218", "SNM": "hololive-bot"},
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
            """.trimIndent()

        val error =
            runCatching {
                factory.send(
                    roomId = 123L,
                    chatRoom = chatRoom,
                    message = "5분 전 알림 · 4건",
                    markdown = false,
                    threadId = null,
                    threadScope = null,
                    mentionsJson = null,
                    requestId = "req-iris-fallback",
                    attachmentJson = attachment,
                )
            }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error.message?.contains("did not create chat log") == true)
        assertEquals(1, linkSender.sendCalls)
        assertEquals(123L, linkSender.roomId)
        assertEquals(123L, commitVerifier.roomId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.rawAttachment)
        assertEquals(
            listOf<String?>(kakaoLinkSpecCommitVerificationAttachment(attachment)),
            commitVerifier.rawAttachments,
        )
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-iris-fallback", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(listOf(kakaoLinkSpecCommitVerificationAttachment(attachment)), commitVerifier.cleanupRawAttachments)
        assertEquals(null, patcher.roomId)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, ShareManager.message)
        assertEquals(null, leverageContexts.match(123L, "5분 전 알림 · 4건", "req-iris-fallback"))
        assertEquals(null, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory fails server generated custom template when chat log commit is not observed`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = false)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"TP": "List", "SDID": "133218"},
              "C": {"HD": {"TD": {"T": "5분 전 알림"}}},
              "K": {"ti": "133218"},
              "template_args": {
                "alarm_title": "5분 전 알림",
                "stream_title": "테스트 방송",
                "web_url": "watch?v=abc"
              }
            }
            """.trimIndent()

        val error =
            runCatching {
                factory.send(
                    roomId = 123L,
                    chatRoom = chatRoom,
                    message = "5분 전 알림",
                    markdown = false,
                    threadId = null,
                    threadScope = null,
                    mentionsJson = null,
                    requestId = "req-template",
                    attachmentJson = attachment,
                )
            }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error.message?.contains("did not create chat log") == true)
        assertEquals(1, linkSender.sendCalls)
        assertEquals(123L, linkSender.roomId)
        assertEquals(123L, commitVerifier.roomId)
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-template", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(listOf(kakaoLinkSpecCommitVerificationAttachment(attachment)), commitVerifier.cleanupRawAttachments)
        assertEquals(null, patcher.roomId)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, ShareManager.message)
        assertEquals(null, FakeTextRequestRecorder.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory cleans pending rows when server generated template send returns false`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val linkSender = RecordingKakaoLinkSpecSender(result = false)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val commitVerifier = RecordingKakaoChatLogCommitVerifier(result = true)
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
                kakaoLinkCommitVerifier = commitVerifier,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeTextRequestCompanion::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(123L))
        val attachment =
            """
            {
              "app_key": "bfbfe8b641716d3f45e01a3b7a03f13d",
              "template_id": "133218",
              "P": {"TP": "List", "SDID": "133218"},
              "C": {"HD": {"TD": {"T": "5분 전 알림"}}},
              "K": {"ti": "133218"},
              "template_args": {
                "alarm_title": "5분 전 알림",
                "stream_title": "테스트 방송",
                "web_url": "watch?v=abc"
              }
            }
            """.trimIndent()

        val error =
            runCatching {
                factory.send(
                    roomId = 123L,
                    chatRoom = chatRoom,
                    message = "5분 전 알림",
                    markdown = false,
                    threadId = null,
                    threadScope = null,
                    mentionsJson = null,
                    requestId = "req-template-false",
                    attachmentJson = attachment,
                )
            }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(1, linkSender.sendCalls)
        assertEquals(null, commitVerifier.roomId)
        assertEquals(123L, commitVerifier.cleanupRoomId)
        assertEquals("req-template-false", commitVerifier.cleanupRequestId)
        assertEquals(kakaoLinkSpecCommitVerificationAttachment(attachment), commitVerifier.cleanupRawAttachment)
        assertEquals(null, patcher.roomId)
        assertEquals(null, ShareManager.chatRoom)
        assertEquals(null, FakeTextRequestRecorder.sendingLog)
    }

    @Test
    fun `factory resolves request singleton from enclosing request class`() {
        FakeTextRequestRecorder.reset()
        val registry = buildFakeRegistry()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { FakeOuterTextRequest.CompanionApi::class.java },
            )
        val chatRoom = FakeChatRoomModel.CompanionResolver.c(FakeRoomEntity(124L))

        val capability = factory.capability()
        factory.send(
            roomId = 124L,
            chatRoom = chatRoom,
            message = "outer singleton",
            markdown = false,
            threadId = null,
            threadScope = null,
            mentionsJson = null,
            attachmentJson = null,
            requestId = "req-outer-text",
        )

        assertTrue(capability.ready)
        assertEquals(chatRoom, FakeTextRequestRecorder.chatRoom)
        val sendingLog = FakeTextRequestRecorder.sendingLog as FakeTextSendingLog
        assertEquals("outer singleton", sendingLog.f0())
        assertEquals(null, FakeTextRequestRecorder.writeType)
        assertEquals(null, FakeTextRequestRecorder.listener)
        assertEquals(null, sendingLog.G)
    }

    @Test
    fun `factory capability fails closed when request method is unavailable`() {
        val factory =
            KakaoTextSendInvocationFactory(
                registry = buildFakeRegistry(),
                logInfo = { _, _ -> },
                requestCompanionClassProvider = { MissingTextRequestCompanion::class.java },
            )

        val capability = factory.capability()

        assertFalse(capability.ready)
        assertTrue(capability.reason?.contains("ChatSendingLogRequest direct text dispatch") == true)
    }

    @Test
    fun `current capabilities exposes ready direct text sender`() {
        val capabilities =
            currentBridgeCapabilities(
                registryAvailable = true,
                registryError = null,
                specReady = true,
                textSendCapability = KakaoTextSendCapability(supported = true, ready = true),
                sendTextEnabled = true,
                sendMarkdownEnabled = true,
            )

        assertTrue(capabilities.sendText.ready)
        assertTrue(capabilities.sendMarkdown.ready)
    }

    @Test
    fun `current capabilities exposes direct text ready by default`() {
        val capabilities =
            currentBridgeCapabilities(
                registryAvailable = true,
                registryError = null,
                specReady = true,
                textSendCapability = KakaoTextSendCapability(supported = true, ready = true),
            )

        assertTrue(capabilities.sendText.ready)
        assertEquals(null, capabilities.sendText.reason)
        assertTrue(capabilities.sendMarkdown.ready)
        assertEquals(null, capabilities.sendMarkdown.reason)
    }

    @Test
    fun `text bridge rollout flags parse truthy values`() {
        assertTrue(ImageBridgeServer.isTextBridgeSendTextEnabled("yes"))
        assertTrue(ImageBridgeServer.isTextBridgeSendMarkdownEnabled("1"))
        assertFalse(ImageBridgeServer.isTextBridgeSendTextEnabled("false"))
        assertTrue(ImageBridgeServer.isTextBridgeSendMarkdownEnabled(null))
    }
}
