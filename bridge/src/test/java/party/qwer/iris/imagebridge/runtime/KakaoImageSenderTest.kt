@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.KakaoImageSender
import kotlin.test.Test
import kotlin.test.assertEquals

class KakaoImageSenderTest {
    @Test
    fun `threaded image send routes through threaded invoker`() {
        val invoker = RecordingKakaoSendInvoker()
        val sender =
            KakaoImageSender(
                chatRoomResolver = { FakeChatRoom() },
                sendInvocationFactory = invoker,
                logInfo = { _, _ -> },
            )

        sender.send(
            roomId = 18478615493603057L,
            imagePaths = listOf("/tmp/thread.png"),
            threadId = 3805486995143352321L,
            threadScope = 3,
            requestId = "req-thread",
        )

        assertEquals(0, invoker.singleCalls)
        assertEquals(0, invoker.multiCalls)
        assertEquals(1, invoker.threadedCalls)
        assertEquals(18478615493603057L, invoker.lastRoomId)
        assertEquals(listOf("/tmp/thread.png"), invoker.lastImagePaths)
        assertEquals(3805486995143352321L, invoker.lastThreadId)
        assertEquals(3, invoker.lastThreadScope)
    }

    @Test
    fun `room image send still routes through single invoker`() {
        val invoker = RecordingKakaoSendInvoker()
        val sender =
            KakaoImageSender(
                chatRoomResolver = { FakeChatRoom() },
                sendInvocationFactory = invoker,
                logInfo = { _, _ -> },
            )

        sender.send(
            roomId = 18478615493603057L,
            imagePaths = listOf("/tmp/room.png"),
            threadId = null,
            threadScope = null,
            requestId = "req-room",
        )

        assertEquals(1, invoker.singleCalls)
        assertEquals(0, invoker.multiCalls)
        assertEquals(0, invoker.threadedCalls)
    }
}
