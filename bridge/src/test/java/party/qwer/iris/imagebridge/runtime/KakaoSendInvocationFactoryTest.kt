@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvocationFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoSendInvocationFactoryTest {
    @Test
    fun `sendSingle uses high quality photo send path across invocations`() {
        FakeMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
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

        assertEquals(listOf("/tmp/first.png", "/tmp/second.png"), FakeMediaSender.multiSentUris)
        assertEquals(FakeMessageType.Photo, FakeMediaSender.multiType)
        assertEquals(FakeWriteType.None, FakeMediaSender.multiWriteType)
        assertEquals(false, FakeMediaSender.multiShareOriginal)
        assertEquals(true, FakeMediaSender.multiHighQuality)
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
    fun `sendSingle uses video enum for explicit mp4 media`() {
        FakeMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildFakeRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        factory.sendSingle(
            chatRoom = FakeChatRoom(),
            imagePath = "/tmp/profile.mp4",
            contentType = "video/mp4",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/profile.mp4"), FakeMediaSender.multiSentUris)
        assertEquals(FakeMessageType.Video, FakeMediaSender.multiType)
        assertEquals(FakeWriteType.None, FakeMediaSender.multiWriteType)
        assertEquals(false, FakeMediaSender.multiShareOriginal)
        assertEquals(true, FakeMediaSender.multiHighQuality)
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
        assertEquals(false, FakeMediaSender.multiShareOriginal)
        assertEquals(true, FakeMediaSender.multiHighQuality)
    }

    @Test
    fun `sendSingle resolves sender constructor from assignable chatRoom parameter`() {
        FakePolymorphicMediaSender.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildPolymorphicRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
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
                pathArgumentFactory = { path -> "uri:$path" },
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
                pathArgumentFactory = { path -> "uri:$path" },
            )

        factory.sendSingle(
            chatRoom = FakeChatRoom(),
            imagePath = "/tmp/primitive-thread.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(listOf("/tmp/primitive-thread.png"), PrimitiveThreadParamMediaSender.sentPaths)
    }

    @Test
    fun `sendThreaded uses 26_4_2 three argument chat media sender constructor`() {
        ModernChatMediaSender26_4_2.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildModernChatMediaSender26_4_2Registry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        factory.sendThreaded(
            roomId = 464252100463241L,
            chatRoom = FakeChatRoom(),
            imagePaths = listOf("/tmp/thread-26-4-2.png"),
            threadId = 3861127076080988161L,
            threadScope = 2,
        )

        assertEquals(3861127076080988161L, ModernChatMediaSender26_4_2.constructorThreadId)
        assertEquals(listOf("/tmp/thread-26-4-2.png"), ModernChatMediaSender26_4_2.sentUris)
        assertEquals(FakeMessageType.Photo, ModernChatMediaSender26_4_2.lastType)
        assertEquals(FakeWriteType.Connect, ModernChatMediaSender26_4_2.lastWriteType)
        assertEquals(false, ModernChatMediaSender26_4_2.lastShareOriginal)
        assertEquals(true, ModernChatMediaSender26_4_2.lastHighQuality)
    }

    @Test
    fun `sendThreaded uses video enum for explicit mp4 media`() {
        ModernChatMediaSender26_4_2.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildModernChatMediaSender26_4_2Registry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        factory.sendThreaded(
            roomId = 464252100463241L,
            chatRoom = FakeChatRoom(),
            imagePaths = listOf("/tmp/profile.mp4"),
            contentTypes = listOf("video/mp4"),
            threadId = 3861127076080988161L,
            threadScope = 2,
        )

        assertEquals(listOf("/tmp/profile.mp4"), ModernChatMediaSender26_4_2.sentUris)
        assertEquals(FakeMessageType.Video, ModernChatMediaSender26_4_2.lastType)
        assertEquals(FakeWriteType.Connect, ModernChatMediaSender26_4_2.lastWriteType)
        assertEquals(false, ModernChatMediaSender26_4_2.lastShareOriginal)
        assertEquals(true, ModernChatMediaSender26_4_2.lastHighQuality)
    }
}
