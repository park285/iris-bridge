package party.qwer.iris.imagebridge.runtime

import android.database.sqlite.SQLiteDatabase
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import party.qwer.iris.imagebridge.runtime.send.KakaoLeverageAttachmentDbPatcher
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KakaoLeverageAttachmentPatcherTest {
    @Test
    fun `patcher reuses writable database across missing row retry batch`() {
        val databasePath = Files.createTempDirectory("iris-kakao-db-test").resolve("KakaoTalk.db")
        seedChatLogs(databasePath)
        var openCount = 0
        val patcher =
            KakaoLeverageAttachmentDbPatcher(
                logInfo = { _, _ -> },
                databasePath = databasePath.toString(),
                clock = { 10_000L },
                executor = Executor { task -> task.run() },
                sleeper = {},
                databaseOpener = { flags ->
                    openCount += 1
                    SQLiteDatabase.openDatabase(databasePath.toString(), null, flags)
                },
            )

        patcher.patchAsync(
            roomId = 123L,
            message = "missing",
            rawAttachment = """{"P":{"TP":"List"}}""",
            requestId = "req-missing",
        )

        assertEquals(1, openCount)
    }

    @Test
    fun `patcher reopens writable database after transient open failure`() {
        val databasePath = Files.createTempDirectory("iris-kakao-db-test").resolve("KakaoTalk.db")
        seedChatLogs(databasePath)
        var openCount = 0
        val patcher =
            KakaoLeverageAttachmentDbPatcher(
                logInfo = { _, _ -> },
                databasePath = databasePath.toString(),
                clock = { 10_000L },
                executor = Executor { task -> task.run() },
                sleeper = {},
                databaseOpener = { flags ->
                    openCount += 1
                    if (openCount == 1) {
                        error("transient open failure")
                    }
                    SQLiteDatabase.openDatabase(databasePath.toString(), null, flags)
                },
            )

        patcher.patchAsync(
            roomId = 123L,
            message = "missing",
            rawAttachment = """{"P":{"TP":"List"}}""",
            requestId = "req-recovered",
        )

        assertEquals(2, openCount)
    }

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

    private fun seedChatLogs(databasePath: Path) {
        SQLiteDatabase
            .openOrCreateDatabase(databasePath.toString(), null)
            .use { database ->
                database.execSQL(
                    """
                    create table chat_logs (
                      _id integer primary key,
                      chat_id integer not null,
                      type integer not null,
                      created_at integer not null,
                      user_id integer not null,
                      message text,
                      attachment text,
                      v text
                    )
                    """.trimIndent(),
                )
            }
    }
}
