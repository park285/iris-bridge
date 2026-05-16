package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.KakaoLeverageAttachmentDbPatcher
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KakaoLeverageAttachmentPatcherTest {
    @Test
    fun `patcher queues work through injected executor`() {
        val tasks = mutableListOf<Runnable>()
        val patcher =
            KakaoLeverageAttachmentDbPatcher(
                logInfo = { _, _ -> },
                databasePath = "/tmp/not-opened.db",
                clock = { 10_000L },
                executor = Executor { task -> tasks += task },
            )

        patcher.patchAsync(
            roomId = 123L,
            message = "message",
            rawAttachment = """{"P":{"TP":"List"}}""",
            requestId = "req-queued",
        )

        assertEquals(1, tasks.size)
    }

    @Test
    fun `patcher logs executor rejection without throwing`() {
        val logs = mutableListOf<String>()
        val patcher =
            KakaoLeverageAttachmentDbPatcher(
                logInfo = { _, message -> logs += message },
                databasePath = "/tmp/not-opened.db",
                clock = { 10_000L },
                executor = Executor { throw RejectedExecutionException("full") },
            )

        patcher.patchAsync(
            roomId = 123L,
            message = "message",
            rawAttachment = """{"P":{"TP":"List"}}""",
            requestId = "req-rejected",
        )

        assertTrue(logs.any { message -> message.contains("patch queue full") })
    }
}
