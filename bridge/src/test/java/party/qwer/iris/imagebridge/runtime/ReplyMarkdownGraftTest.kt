package party.qwer.iris.imagebridge.runtime

import org.json.JSONObject
import party.qwer.iris.ReplyHookSignatureProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.mentionsHashFromJson
import party.qwer.iris.imagebridge.runtime.core.replyHookSign
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownBridgeExtras
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownIngressCapture
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionBridgeExtras
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionSendingLogAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BRIDGE_TOKEN = "bridge-token"

class ReplyMarkdownPendingContextStoreTest {
    @Test
    fun `match removes first exact room and message context`() {
        var now = 1_000L
        val store = ReplyMarkdownPendingContextStore(clock = { now })
        val context =
            ReplyMarkdownPendingContext(
                roomId = 7L,
                messageText = "hello",
                threadId = 55L,
                threadScope = 2,
                createdAtEpochMs = now,
            )

        store.remember(context)

        assertEquals(context, store.match(roomId = 7L, messageText = "hello"))
        assertNull(store.match(roomId = 7L, messageText = "hello"))
    }

    @Test
    fun `match skips stale entries`() {
        var now = 1_000L
        val store = ReplyMarkdownPendingContextStore(ttlMs = 100L, clock = { now })
        store.remember(
            ReplyMarkdownPendingContext(
                roomId = 7L,
                messageText = "stale",
                threadId = 1L,
                threadScope = 2,
                createdAtEpochMs = now,
            ),
        )

        now += 101L

        assertNull(store.match(roomId = 7L, messageText = "stale"))
        assertEquals(0, store.size())
    }

    @Test
    fun `message fallback drops stale duplicate contexts and uses newest capture`() {
        val store = ReplyMarkdownPendingContextStore()
        val first =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "same",
                threadId = 10L,
                threadScope = 2,
                sessionId = "first",
                createdAtEpochMs = 1L,
            )
        val second =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "same",
                threadId = 11L,
                threadScope = 3,
                sessionId = "second",
                createdAtEpochMs = 2L,
            )

        store.remember(first)
        store.remember(second)

        assertEquals(second, store.match(roomId = 9L, messageText = "same"))
        assertNull(store.match(roomId = 9L, messageText = "same"))
    }

    @Test
    fun `same message in same room matches by session id when present`() {
        val store = ReplyMarkdownPendingContextStore()
        val first =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "same",
                threadId = 10L,
                threadScope = 2,
                sessionId = "first",
                createdAtEpochMs = 1L,
            )
        val second =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "same",
                threadId = 11L,
                threadScope = 3,
                sessionId = "second",
                createdAtEpochMs = 2L,
            )

        store.remember(first)
        store.remember(second)

        assertEquals(first, store.match(roomId = 9L, messageText = "same", sessionId = "first"))
        assertEquals(second, store.match(roomId = 9L, messageText = "same", sessionId = "second"))
    }

    @Test
    fun `session specific match ignores message drift when session id is present`() {
        val store = ReplyMarkdownPendingContextStore()
        val context =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "[KST 02:42:31]\n\n원본 답변",
                threadId = 10L,
                threadScope = 2,
                sessionId = "session-1",
                createdAtEpochMs = 1L,
            )

        store.remember(context)

        assertEquals(
            context,
            store.match(
                roomId = 9L,
                messageText = "[KST 02:42:31] 원본 답변",
                sessionId = "session-1",
            ),
        )
    }

    @Test
    fun `session specific match prefers exact session over older sessionless entry`() {
        val store = ReplyMarkdownPendingContextStore()
        val sessionless =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "same",
                threadId = 10L,
                threadScope = 2,
                sessionId = null,
                createdAtEpochMs = 1L,
            )
        val sessionSpecific =
            ReplyMarkdownPendingContext(
                roomId = 9L,
                messageText = "same",
                threadId = 11L,
                threadScope = 3,
                sessionId = "session-1",
                createdAtEpochMs = 2L,
            )

        store.remember(sessionless)
        store.remember(sessionSpecific)

        assertEquals(sessionSpecific, store.match(roomId = 9L, messageText = "mutated", sessionId = "session-1"))
        assertEquals(sessionless, store.match(roomId = 9L, messageText = "same"))
    }

    @Test
    fun `ingress capture stores extracted context`() {
        val store = ReplyMarkdownPendingContextStore()
        val captured =
            ReplyMarkdownIngressCapture.capture(
                intent = null,
                store = store,
                onCaptured = {},
            )

        assertNull(captured)
        val extracted =
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    sessionId = "session-capture",
                    roomIdRaw = "18478615493603057",
                    threadIdRaw = "3805486995143352321",
                    threadScope = 2,
                    createdAtEpochMs = 1_000L,
                    signature = markdownSignature(18478615493603057L, "thread text", "session-capture", 1_000L),
                    messageText = "thread text",
                ),
                nowEpochMs = 1_001L,
                bridgeToken = BRIDGE_TOKEN,
            )
        assertNotNull(extracted)
        store.remember(extracted)
        assertEquals(extracted, store.match(roomId = 18478615493603057L, messageText = "thread text"))
    }
}

class ReplyMarkdownBridgeExtrasTest {
    @Test
    fun `extract pending context reads extras and nested message`() {
        val context =
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    sessionId = "session-1",
                    roomIdRaw = "18478615493603057",
                    threadIdRaw = "3805486995143352321",
                    threadScope = 3,
                    createdAtEpochMs = 99L,
                    signature = markdownSignature(18478615493603057L, "markdown text", "session-1", 99L),
                    nestedMessageText = "markdown text",
                ),
                nowEpochMs = 100L,
                bridgeToken = BRIDGE_TOKEN,
            )

        assertNotNull(context)
        assertEquals(18478615493603057L, context.roomId)
        assertEquals("markdown text", context.messageText)
        assertEquals(3805486995143352321L, context.threadId)
        assertEquals(3, context.threadScope)
        assertEquals("session-1", context.sessionId)
        assertEquals(99L, context.createdAtEpochMs)
    }

    @Test
    fun `extract pending context defaults scope to two`() {
        val context =
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    sessionId = "session-2",
                    roomIdRaw = "1",
                    threadIdRaw = "2",
                    createdAtEpochMs = 7L,
                    signature = markdownSignature(1L, "hi", "session-2", 7L),
                    messageText = "hi",
                ),
                nowEpochMs = 8L,
                bridgeToken = BRIDGE_TOKEN,
            )

        assertNotNull(context)
        assertEquals(2, context.threadScope)
        assertEquals(7L, context.createdAtEpochMs)
    }

    @Test
    fun `extract pending context accepts numeric snapshot values`() {
        val context =
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    sessionId = "session-3",
                    roomIdRaw = 18478615493603057L.toString(),
                    threadIdRaw = 3805486995143352321L.toString(),
                    threadScope = 2,
                    createdAtEpochMs = 99L,
                    signature = markdownSignature(18478615493603057L, "numeric extras", "session-3", 99L),
                    messageText = "numeric extras",
                ),
                nowEpochMs = 100L,
                bridgeToken = BRIDGE_TOKEN,
            )

        assertNotNull(context)
        assertEquals(18478615493603057L, context.roomId)
        assertEquals(3805486995143352321L, context.threadId)
        assertEquals(2, context.threadScope)
        assertEquals(99L, context.createdAtEpochMs)
    }

    @Test
    fun `extract pending context skips non threaded markdown intents`() {
        assertNull(
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    roomIdRaw = "1",
                    messageText = "hi",
                ),
            ),
        )
    }

    @Test
    fun `extract pending context skips missing signature`() {
        assertNull(
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    sessionId = "session-4",
                    roomIdRaw = "1",
                    threadIdRaw = "2",
                    createdAtEpochMs = 1_000L,
                    messageText = "hi",
                ),
                nowEpochMs = 1_001L,
                bridgeToken = BRIDGE_TOKEN,
            ),
        )
    }

    @Test
    fun `extract pending context skips expired signature`() {
        assertNull(
            ReplyMarkdownBridgeExtras.extractPendingContext(
                ReplyMarkdownBridgeExtras.Snapshot(
                    sessionId = "session-5",
                    roomIdRaw = "1",
                    threadIdRaw = "2",
                    createdAtEpochMs = 1_000L,
                    signature = markdownSignature(1L, "hi", "session-5", 1_000L),
                    messageText = "hi",
                ),
                nowEpochMs = 1_000L + ReplyHookSignatureProtocol.TTL_MS + 1,
                bridgeToken = BRIDGE_TOKEN,
            ),
        )
    }
}

class ReplyMentionPendingContextStoreTest {
    @Test
    fun `sessionless mention context is ignored`() {
        var now = 1_000L
        val store = ReplyMentionPendingContextStore(clock = { now })
        val context =
            ReplyMentionPendingContext(
                roomId = 7L,
                messageText = "@홍길동 테스트",
                attachmentText = """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
                createdAtEpochMs = now,
            )

        store.remember(context)

        assertNull(store.match(roomId = 7L, messageText = "@홍길동 테스트"))
        assertNull(store.match(roomId = 7L, messageText = "@홍길동 테스트"))
    }

    @Test
    fun `session specific mention match ignores message drift`() {
        val store = ReplyMentionPendingContextStore()
        val context =
            ReplyMentionPendingContext(
                roomId = 9L,
                messageText = "@홍길동\n\n테스트",
                attachmentText = """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
                sessionId = "session-1",
                createdAtEpochMs = 1L,
            )

        store.remember(context)

        assertNull(store.match(roomId = 9L, messageText = "@홍길동 테스트"))
        assertEquals(context, store.match(roomId = 9L, messageText = "@홍길동 테스트", sessionId = "session-1"))
    }
}

class ReplyMentionBridgeExtrasTest {
    @Test
    fun `extract pending mention context reads nested message and attachment`() {
        val context =
            ReplyMentionBridgeExtras.extractPendingContext(
                ReplyMentionBridgeExtras.Snapshot(
                    sessionId = "session-1",
                    fallbackRoomId = 18478615493603057L,
                    createdAtEpochMs = 99L,
                    signature =
                        mentionSignature(
                            roomId = 18478615493603057L,
                            messageText = "@홍길동 테스트",
                            sessionId = "session-1",
                            createdAtEpochMs = 99L,
                            mentionsJson = """{"mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
                        ),
                    nestedMessageText = "@홍길동 테스트",
                    nestedAttachmentText = """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
                ),
                nowEpochMs = 100L,
                bridgeToken = BRIDGE_TOKEN,
            )

        assertNotNull(context)
        assertEquals(18478615493603057L, context.roomId)
        assertEquals("@홍길동 테스트", context.messageText)
        assertEquals("session-1", context.sessionId)
        assertEquals(99L, context.createdAtEpochMs)
        val mention = JSONObject(context.attachmentText).getJSONArray("mentions").getJSONObject(0)
        assertEquals(123456789L, mention.getLong("user_id"))
    }

    @Test
    fun `extract pending mention context skips attachment without mentions`() {
        assertNull(
            ReplyMentionBridgeExtras.extractPendingContext(
                ReplyMentionBridgeExtras.Snapshot(
                    fallbackRoomId = 1L,
                    messageText = "hello",
                    attachmentText = """{"callingPkg":"com.kakao.talk","markdown":true}""",
                ),
            ),
        )
    }

    @Test
    fun `extract pending mention context skips wrong mentions hash`() {
        assertNull(
            ReplyMentionBridgeExtras.extractPendingContext(
                ReplyMentionBridgeExtras.Snapshot(
                    sessionId = "session-2",
                    fallbackRoomId = 1L,
                    createdAtEpochMs = 100L,
                    signature =
                        mentionSignature(
                            roomId = 1L,
                            messageText = "@홍길동 테스트",
                            sessionId = "session-2",
                            createdAtEpochMs = 100L,
                            mentionsJson = """{"mentions":[{"user_id":999,"at":[1],"len":3}]}""",
                        ),
                    messageText = "@홍길동 테스트",
                    attachmentText = """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
                ),
                nowEpochMs = 101L,
                bridgeToken = BRIDGE_TOKEN,
            ),
        )
    }
}

class ReplyMarkdownSendingLogAccessTest {
    @Test
    fun `reads sending log room and message`() {
        val log = FakeSendingLogWithSetters()
        log.message = "hello"
        log.roomId = 12L

        assertEquals(12L, ReplyMarkdownSendingLogAccess.readRoomId(log))
        assertEquals("hello", ReplyMarkdownSendingLogAccess.readMessageText(log))
    }

    @Test
    fun `reads sending log message from modern getter`() {
        val log = FakeSendingLogWithModernMessageGetter("hello")

        assertEquals("hello", ReplyMarkdownSendingLogAccess.readMessageText(log))
    }

    @Test
    fun `reads sending log message from Kakao 26 getter`() {
        val log = FakeSendingLogWithKakao26MessageGetter("hello")

        assertEquals("hello", ReplyMarkdownSendingLogAccess.readMessageText(log))
    }

    @Test
    fun `prefers string message getter over non string legacy getter`() {
        val log = FakeSendingLogWithNonStringLegacyGetter("hello")

        assertEquals("hello", ReplyMarkdownSendingLogAccess.readMessageText(log))
    }

    @Test
    fun `writes thread metadata to stable fields ignoring version-shifted trap setter`() {
        val log = FakeSendingLogWithSetters()

        ReplyMarkdownSendingLogAccess.writeThreadMetadata(log, threadId = 123L, threadScope = 3)

        assertEquals(123L, log.V0)
        assertEquals(3, log.Z)
        assertEquals(-1, log.multiUploadSequence)
    }

    @Test
    fun `writes thread metadata through fields when setters are absent`() {
        val log = FakeSendingLogWithFields()

        ReplyMarkdownSendingLogAccess.writeThreadMetadata(log, threadId = 456L, threadScope = 2)

        assertEquals(456L, log.V0)
        assertEquals(2, log.Z)
    }

    @Test
    fun `reads session id from string attachment`() {
        val log = FakeSendingLogWithAttachmentField("""{"callingPkg":"com.kakao.talk","irisSessionId":"session-1"}""")

        assertEquals("session-1", ReplyMarkdownSendingLogAccess.readAttachmentSessionId(log))
    }

    @Test
    fun `reads session id from renamed attachment field`() {
        val log = FakeSendingLogWithRenamedAttachmentField("""{"callingPkg":"com.kakao.talk","irisSessionId":"session-2"}""")

        assertEquals("session-2", ReplyMarkdownSendingLogAccess.readAttachmentSessionId(log))
    }

    @Test
    fun `reads session id from nested attachment object`() {
        val log =
            FakeSendingLogWithNestedAttachmentField(
                FakeAttachmentPayload("""{"callingPkg":"com.kakao.talk","irisSessionId":"session-3"}"""),
            )

        assertEquals("session-3", ReplyMarkdownSendingLogAccess.readAttachmentSessionId(log))
    }

    @Test
    fun `injects pending mention attachment into json object sending log attachment`() {
        val log = FakeSendingLogWithJsonAttachmentField(JSONObject("""{"callingPkg":"com.kakao.talk","markdown":true}"""))

        assertTrue(
            ReplyMentionSendingLogAccess.injectMentionAttachment(
                log,
                """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
            ),
        )

        assertTrue(log.attachmentPayload.getBoolean("markdown"))
        val mention = log.attachmentPayload.getJSONArray("mentions").getJSONObject(0)
        assertEquals(123456789L, mention.getLong("user_id"))
        assertEquals(1, mention.getJSONArray("at").getInt(0))
        assertEquals(3, mention.getInt("len"))
    }

    @Test
    fun `does not inject mention attachment without pending context`() {
        val log =
            FakeSendingLogWithAttachmentField(
                """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
            )

        assertFalse(ReplyMentionSendingLogAccess.injectMentionAttachment(log))
    }

    @Test
    fun `injects pending mention attachment into stripped sending log attachment`() {
        val log = FakeSendingLogWithAttachmentField("""{"callingPkg":"com.kakao.talk","markdown":true}""")

        assertTrue(
            ReplyMentionSendingLogAccess.injectMentionAttachment(
                log,
                """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":123456789,"at":[1],"len":3}]}""",
            ),
        )

        val attachment = JSONObject(log.G)
        assertTrue(attachment.getBoolean("markdown"))
        val mention = attachment.getJSONArray("mentions").getJSONObject(0)
        assertEquals(123456789L, mention.getLong("user_id"))
        assertEquals(1, mention.getJSONArray("at").getInt(0))
        assertEquals(3, mention.getInt("len"))
    }

    @Test
    fun `injects pending mention attachment when ShareManager text log has no attachment`() {
        val log = FakeSendingLogWithNullableAttachmentField(null)

        assertTrue(
            ReplyMentionSendingLogAccess.injectMentionAttachment(
                log,
                """{"callingPkg":"com.kakao.talk","mentions":[{"user_id":"text-user","at":[1],"len":3}]}""",
            ),
        )

        val attachment = JSONObject(assertNotNull(log.G))
        val mention = attachment.getJSONArray("mentions").getJSONObject(0)
        assertEquals("text-user", mention.getString("user_id"))
        assertEquals("com.kakao.talk", attachment.getString("callingPkg"))
    }
}

class ReplyMarkdownRequestSelectorTest {
    @Test
    fun `selector returns request methods with expected parameter types`() {
        val selected =
            selectReplyMarkdownRequestHookMethodsForTest(
                FakeRequestCompanion::class.java,
                chatRoomClass = FakeMarkdownChatRoom::class.java,
                writeTypeClass = FakeMarkdownWriteType::class.java,
                listenerClass = FakeMarkdownListener::class.java,
            )

        assertEquals(listOf("u", "v"), selected.map { it.name })
        assertEquals(
            listOf(
                FakeMarkdownChatRoom::class.java,
                Any::class.java,
                FakeMarkdownWriteType::class.java,
                FakeMarkdownListener::class.java,
                Boolean::class.javaPrimitiveType,
            ),
            selected.first().parameterTypes.toList(),
        )
        assertTrue(selected.none { method -> method.parameterTypes[0] == FakeMarkdownOtherChatRoom::class.java })
    }

    @Test
    fun `selector falls back to obfuscated u method when registry types are unavailable`() {
        val selected =
            selectReplyMarkdownRequestHookMethodForTest(
                FakeRequestCompanion::class.java,
                chatRoomClass = null,
                writeTypeClass = null,
                listenerClass = null,
            )

        assertNotNull(selected)
        assertEquals("u", selected.name)
    }
}

private class FakeSendingLogWithSetters {
    var roomId: Long = 0L
    var message: String = ""

    @Suppress("PropertyName")
    var V0: Long? = null

    @Suppress("PropertyName")
    var Z: Int = 0

    var multiUploadSequence: Int = -1

    fun getChatRoomId(): Long = roomId

    fun f0(): String = message

    // 26.5.2에서 H1(int)는 thread scope가 아니라 multiUploadSequence setter다 — writeThreadMetadata가 호출하면 안 된다.
    @Suppress("FunctionName")
    fun H1(value: Int) {
        multiUploadSequence = value
    }
}

private class FakeSendingLogWithModernMessageGetter(
    private val message: String,
) {
    fun getMessage(): String = message
}

private class FakeSendingLogWithKakao26MessageGetter(
    private val message: String,
) {
    fun g0(): String = message
}

private class FakeSendingLogWithNonStringLegacyGetter(
    private val message: String,
) {
    fun f0(): Any = Any()

    fun getMessage(): String = message
}

private class FakeSendingLogWithFields {
    @Suppress("PropertyName")
    var V0: Long? = null

    @Suppress("PropertyName")
    var Z: Int = 0
}

private class FakeSendingLogWithAttachmentField(
    @Suppress("PropertyName")
    var G: String,
)

private class FakeSendingLogWithNullableAttachmentField(
    @Suppress("PropertyName")
    var G: String?,
)

private class FakeSendingLogWithRenamedAttachmentField(
    var attachmentPayload: String,
)

private class FakeSendingLogWithNestedAttachmentField(
    var attachmentPayload: FakeAttachmentPayload,
)

private class FakeSendingLogWithJsonAttachmentField(
    var attachmentPayload: JSONObject,
)

private class FakeAttachmentPayload(
    var raw: String,
)

private class FakeMarkdownChatRoom

private class FakeMarkdownOtherChatRoom

private class FakeMarkdownWriteType

private interface FakeMarkdownListener

private fun markdownSignature(
    roomId: Long,
    messageText: String,
    sessionId: String,
    createdAtEpochMs: Long,
): String =
    requireNotNull(
        BridgeCore.replyHookSign(
            bridgeToken = BRIDGE_TOKEN,
            roomId = roomId,
            messageText = messageText,
            sessionId = sessionId,
            createdAtEpochMs = createdAtEpochMs,
            mentionsHash = null,
        ),
    )

private fun mentionSignature(
    roomId: Long,
    messageText: String,
    sessionId: String,
    createdAtEpochMs: Long,
    mentionsJson: String,
): String =
    requireNotNull(
        BridgeCore.replyHookSign(
            bridgeToken = BRIDGE_TOKEN,
            roomId = roomId,
            messageText = messageText,
            sessionId = sessionId,
            createdAtEpochMs = createdAtEpochMs,
            mentionsHash = BridgeCore.mentionsHashFromJson(mentionsJson),
        ),
    )

@Suppress("UNUSED_PARAMETER")
private class FakeRequestCompanion {
    fun u(
        chatRoom: FakeMarkdownOtherChatRoom,
        sendingLog: Any,
        writeType: FakeMarkdownWriteType,
        listener: FakeMarkdownListener,
        immediate: Boolean,
    ) = Unit

    fun u(
        chatRoom: FakeMarkdownChatRoom,
        sendingLog: Any,
        writeType: FakeMarkdownWriteType,
        listener: FakeMarkdownListener,
        immediate: Boolean,
    ) = Unit

    fun v(
        chatRoom: FakeMarkdownChatRoom,
        sendingLog: Any,
        writeType: FakeMarkdownWriteType,
        listener: FakeMarkdownListener,
        immediate: Boolean,
    ) = Unit
}
