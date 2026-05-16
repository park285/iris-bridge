@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.selectThreadedImageInjectBindingsForTest
import party.qwer.iris.imagebridge.runtime.send.selectThreadedImageInjectMethodForTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadedImageXposedInjectorSelectorTest {
    @Test
    fun `threaded image injector selector resolves method by signature when obfuscated name changes`() {
        val method =
            selectThreadedImageInjectMethodForTest(
                chatMediaSenderClass = RenamedThreadedInjectMediaSender::class.java,
                writeTypeClass = FakeWriteType::class.java,
                listenerClass = FakeListener::class.java,
            )

        assertEquals("z", method.name)
    }

    @Test
    fun `threaded image injector prefers request dispatch hook when available`() {
        val bindings =
            selectThreadedImageInjectBindingsForTest(
                requestCompanionClass = FakeThreadedRequestCompanion::class.java,
                chatMediaSenderClass = RenamedThreadedInjectMediaSender::class.java,
                chatRoomClass = FakeChatRoomModel::class.java,
                writeTypeClass = FakeWriteType::class.java,
                listenerClass = FakeListener::class.java,
            )

        assertEquals(listOf("request", "legacy"), bindings.map { it.source })
        assertEquals(listOf("u", "z"), bindings.map { it.method.name })
        assertEquals(listOf(1, 0), bindings.map { it.sendingLogArgIndex })
    }
}
