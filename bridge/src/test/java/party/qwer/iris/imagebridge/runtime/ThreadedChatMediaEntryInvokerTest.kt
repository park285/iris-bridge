@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.ThreadedChatMediaEntryInvoker
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadedChatMediaEntryInvokerTest {
    @Test
    fun `threaded entry invoker resolves method by signature when obfuscated name changes`() {
        RenamedThreadedEntryMediaSender.reset()
        val invoker =
            ThreadedChatMediaEntryInvoker(
                registry = buildRenamedThreadedRegistry(),
                pathArgumentFactory = { path -> "uri:$path" },
            )

        invoker.invoke(
            sender =
                RenamedThreadedEntryMediaSender(
                    chatRoom = FakeChatRoom(),
                    threadId = 3805486995143352321L,
                    sendWithThread = { false },
                    attachmentDecorator = { payload -> payload },
                ),
            imagePaths = listOf("/tmp/thread-a.png", "/tmp/thread-b.png"),
        )

        assertEquals(listOf("/tmp/thread-a.png", "/tmp/thread-b.png"), RenamedThreadedEntryMediaSender.sentUris)
        assertEquals(FakeMessageType.MultiPhoto, RenamedThreadedEntryMediaSender.lastType)
        assertEquals(FakeWriteType.Connect, RenamedThreadedEntryMediaSender.lastWriteType)
    }
}
