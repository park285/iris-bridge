package party.qwer.iris.imagebridge.runtime

import android.database.sqlite.SQLiteDatabase
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import party.qwer.iris.imagebridge.runtime.send.KakaoChatLogDbCommitVerifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KakaoChatLogDbCommitVerifierTest {
    @Test
    fun `awaitCommitted reuses read database across retry batch and sees later committed rows`() {
        val databasePath = Files.createTempDirectory("iris-kakao-db-test").resolve("KakaoTalk.db")
        seedChatLogs(databasePath)
        var openCount = 0
        var sleepCount = 0
        val verifier =
            KakaoChatLogDbCommitVerifier(
                databasePath = databasePath.toString(),
                sleeper = {
                    sleepCount += 1
                    if (sleepCount == 2) {
                        insertChatLog(databasePath, id = 7L, chatId = 123L, createdAt = 101L, message = "delayed")
                    }
                },
                databaseOpener = { flags ->
                    openCount += 1
                    SQLiteDatabase.openDatabase(databasePath.toString(), null, flags)
                },
            )

        val committed =
            verifier.awaitCommitted(
                roomId = 123L,
                message = "delayed",
                minimumCreatedAt = 100L,
                minimumRowId = 0L,
                requestId = "req-delayed",
            )

        assertTrue(committed)
        assertEquals(1, openCount)
    }

    @Test
    fun `awaitCommitted closes failed open attempt and retries with a fresh database`() {
        val databasePath = Files.createTempDirectory("iris-kakao-db-test").resolve("KakaoTalk.db")
        seedChatLogs(databasePath)
        insertChatLog(databasePath, id = 7L, chatId = 123L, createdAt = 101L, message = "recovered")
        var openCount = 0
        val verifier =
            KakaoChatLogDbCommitVerifier(
                logInfo = { _, _ -> },
                databasePath = databasePath.toString(),
                sleeper = {},
                databaseOpener = { flags ->
                    openCount += 1
                    if (openCount == 1) {
                        error("transient open failure")
                    }
                    SQLiteDatabase.openDatabase(databasePath.toString(), null, flags)
                },
            )

        val committed =
            verifier.awaitCommitted(
                roomId = 123L,
                message = "recovered",
                minimumCreatedAt = 100L,
                minimumRowId = 0L,
                requestId = "req-recovered",
            )

        assertTrue(committed)
        assertEquals(2, openCount)
    }

    @Test
    fun `cleanupPendingKakaoLinkSendingLogs deletes only matching pending kakaolink rows`() {
        val databasePath = Files.createTempDirectory("iris-kakao-db-test").resolve("KakaoTalk.db")
        seedSendingLogs(databasePath.toString())
        val verifier = KakaoChatLogDbCommitVerifier(databasePath = databasePath.toString(), sleeper = {})

        val cleaned =
            verifier.cleanupPendingKakaoLinkSendingLogs(
                roomId = 123L,
                minimumCreatedAt = 100L,
                requestId = "req-cleanup",
                rawAttachment = expectedAttachment("방송 알림"),
            )

        assertTrue(cleaned)
        assertEquals(listOf(1L, 3L, 4L, 5L, 6L), sendingLogIds(databasePath.toString()))
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

    private fun insertChatLog(
        databasePath: Path,
        id: Long,
        chatId: Long,
        createdAt: Long,
        message: String,
    ) {
        SQLiteDatabase
            .openDatabase(databasePath.toString(), null, SQLiteDatabase.OPEN_READWRITE)
            .use { database ->
                database.execSQL(
                    """
                    insert into chat_logs (_id, chat_id, type, created_at, user_id, message, attachment, v)
                    values (?, ?, 71, ?, 1, ?, null, '{}')
                    """.trimIndent(),
                    arrayOf<Any>(id, chatId, createdAt, message),
                )
            }
    }

    private fun seedSendingLogs(databasePath: String) {
        SQLiteDatabase
            .openOrCreateDatabase(databasePath, null)
            .use { database ->
                database.execSQL(
                    """
                    create table chat_sending_logs (
                      _id integer primary key,
                      chat_id integer not null,
                      type integer not null,
                      created_at integer not null,
                      attachment text
                    )
                    """.trimIndent(),
                )
                insertSendingLog(database, 1L, 123L, 71, 99L, expectedAttachment("방송 알림"))
                insertSendingLog(database, 2L, 123L, 71, 100L, pendingAttachment("방송 알림"))
                insertSendingLog(database, 3L, 123L, 71, 100L, pendingAttachment("다른 알림"))
                insertSendingLog(database, 4L, 456L, 71, 100L, pendingAttachment("방송 알림"))
                insertSendingLog(database, 5L, 123L, 1, 100L, pendingAttachment("방송 알림"))
                insertSendingLog(database, 6L, 123L, 71, 100L, pendingAttachment("방송 알림", "watch?v=stale00002"))
            }
    }

    private fun insertSendingLog(
        database: SQLiteDatabase,
        id: Long,
        chatId: Long,
        type: Int,
        createdAt: Long,
        attachment: String,
    ) {
        database.execSQL(
            "insert into chat_sending_logs (_id, chat_id, type, created_at, attachment) values (?, ?, ?, ?, ?)",
            arrayOf<Any>(id, chatId, type, createdAt, attachment),
        )
    }

    private fun sendingLogIds(databasePath: String): List<Long> =
        SQLiteDatabase
            .openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY)
            .use { database ->
                database
                    .rawQuery("select _id from chat_sending_logs order by _id", emptyArray())
                    .use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(cursor.getLong(0))
                            }
                        }
                    }
            }

    private fun expectedAttachment(
        title: String,
        webUrl: String = "watch?v=expected01",
    ): String =
        """
        {
          "template_id": "133266",
          "C": {"HD": {"TD": {"T": "$title"}}},
          "K": {"ti": "133266"},
          "template_args": {"alarm_title": "$title", "web_url": "$webUrl"}
        }
        """.trimIndent()

    private fun pendingAttachment(
        title: String,
        webUrl: String = "watch?v=expected01",
    ): String =
        """
        {
          "ti": "133266",
          "C": {"HD": {"TD": {"T": "$title"}}},
          "ta": {"alarm_title": "$title", "web_url": "$webUrl"}
        }
        """.trimIndent()
}
