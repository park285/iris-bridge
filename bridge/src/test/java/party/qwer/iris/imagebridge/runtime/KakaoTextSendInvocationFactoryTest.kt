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
    fun `factory does not patch server generated custom template attachment`() {
        FakeTextRequestRecorder.reset()
        ShareManager.reset()
        val registry = buildFakeRegistry()
        val leverageCommitContexts = ReplyLeveragePendingContextStore()
        val linkSender = RecordingKakaoLinkSpecSender(result = true)
        val patcher = RecordingKakaoLeverageAttachmentPatcher()
        val factory =
            KakaoTextSendInvocationFactory(
                registry = registry,
                context = Application(),
                leverageCommitPendingContexts = leverageCommitContexts,
                kakaoLinkSpecSender = linkSender,
                leverageAttachmentPatcher = patcher,
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
        assertEquals(attachment, linkSender.rawAttachment)
        assertEquals(null, patcher.roomId)
        assertEquals(null, patcher.rawAttachment)
        assertEquals(null, leverageCommitContexts.match(123L, "5분 전 알림", "req-template"))
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
