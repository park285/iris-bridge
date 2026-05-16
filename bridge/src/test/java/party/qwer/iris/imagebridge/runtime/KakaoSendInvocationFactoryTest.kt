@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvocationFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoSendInvocationFactoryTest {
    @Test
    fun `sendSingle caches reflection classes across invocations`() {
        FakeMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
            )
        val chatRoom = FakeChatRoom()

        factory.sendSingle(
            chatRoom = chatRoom,
            imagePath = "/tmp/first.png",
            threadId = 7L,
            threadScope = 3,
        )
        factory.sendSingle(
            chatRoom = chatRoom,
            imagePath = "/tmp/second.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/first.png", "/tmp/second.png"), FakeMediaSender.sentPaths)
        assertEquals(listOf(true, false), FakeMediaSender.threadFlags)
    }

    @Test
    fun `sendSingle rejects missing image path`() {
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
            )

        assertFailsWith<IllegalArgumentException> {
            factory.sendSingle(
                chatRoom = FakeChatRoom(),
                imagePath = "",
                threadId = null,
                threadScope = null,
            )
        }
    }

    @Test
    fun `sendMultiple uses uri list and multi photo enum`() {
        FakeMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        factory.sendMultiple(
            chatRoom = FakeChatRoom(),
            imagePaths = listOf("/tmp/a.png", "/tmp/b.png"),
            threadId = 1L,
            threadScope = 3,
        )

        assertEquals(listOf("/tmp/a.png", "/tmp/b.png"), FakeMediaSender.multiSentUris)
        assertEquals(FakeMessageType.MultiPhoto, FakeMediaSender.multiType)
        assertEquals(FakeWriteType.None, FakeMediaSender.multiWriteType)
    }

    @Test
    fun `sendSingle resolves sender constructor from assignable chatRoom parameter`() {
        FakePolymorphicMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildPolymorphicRegistry(),
            )

        factory.sendSingle(
            chatRoom = FakeDerivedChatRoom(),
            imagePath = "/tmp/polymorphic.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/polymorphic.png"), FakePolymorphicMediaSender.sentPaths)
    }

    @Test
    fun `sendSingle prefers exact sender constructor over assignable one`() {
        ExactPreferredMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildExactPreferredRegistry(),
            )

        factory.sendSingle(
            chatRoom = FakeDerivedChatRoom(),
            imagePath = "/tmp/exact.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(1, ExactPreferredMediaSender.exactCalls)
        assertEquals(0, ExactPreferredMediaSender.baseCalls)
    }

    @Test
    fun `sendSingle accepts sender constructor with primitive long thread parameter`() {
        PrimitiveThreadParamMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildPrimitiveThreadParamRegistry(),
            )

        factory.sendSingle(
            chatRoom = FakeChatRoom(),
            imagePath = "/tmp/primitive-thread.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/primitive-thread.png"), PrimitiveThreadParamMediaSender.sentPaths)
    }
}
