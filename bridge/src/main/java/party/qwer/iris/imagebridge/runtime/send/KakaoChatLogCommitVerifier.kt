package party.qwer.iris.imagebridge.runtime.send

import android.database.sqlite.SQLiteDatabase
import android.util.Log

internal const val KAKAO_TALK_DATABASE_PATH = "/data/data/com.kakao.talk/databases/KakaoTalk.db"

internal interface KakaoChatLogCommitVerifier {
    fun latestCommittedRowId(roomId: Long): Long = 0L

    fun awaitCommitted(
        roomId: Long,
        message: String,
        minimumCreatedAt: Long,
        minimumRowId: Long,
        requestId: String?,
        rawAttachment: String? = null,
    ): Boolean
}

internal class KakaoChatLogDbCommitVerifier(
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
    private val databasePath: String = KAKAO_TALK_DATABASE_PATH,
    private val sleeper: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
) : KakaoChatLogCommitVerifier {
    override fun awaitCommitted(
        roomId: Long,
        message: String,
        minimumCreatedAt: Long,
        minimumRowId: Long,
        requestId: String?,
        rawAttachment: String?,
    ): Boolean {
        repeat(COMMIT_ATTEMPTS) { attempt ->
            if (!sleepBeforeRetry(requestId, roomId)) return false
            val committed =
                runCatching {
                    hasCommittedRow(roomId, message, minimumCreatedAt, minimumRowId, rawAttachment)
                }.onFailure { error ->
                    logInfo(
                        KAKAO_TEXT_SEND_TAG,
                        "kakaolink commit check failed requestId=$requestId room=$roomId " +
                            "attempt=${attempt + 1} error=${error.javaClass.name}: ${error.message}",
                    )
                }.getOrDefault(false)
            if (committed) {
                logInfo(KAKAO_TEXT_SEND_TAG, "kakaolink commit verified requestId=$requestId room=$roomId attempt=${attempt + 1}")
                return true
            }
        }
        logInfo(KAKAO_TEXT_SEND_TAG, "kakaolink commit not found requestId=$requestId room=$roomId minimumRowId=$minimumRowId")
        return false
    }

    override fun latestCommittedRowId(roomId: Long): Long =
        runCatching {
            val db =
                SQLiteDatabase.openDatabase(
                    databasePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )
            db.use { database ->
                database
                    .rawQuery(
                        """
                        select coalesce(max(_id), 0)
                        from chat_logs
                        where chat_id=? and type=71
                        """.trimIndent(),
                        arrayOf(roomId.toString()),
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                    }
            }
        }.getOrElse { error ->
            val message = "kakaolink commit baseline failed room=$roomId error=${error.javaClass.name}: ${error.message}"
            logInfo(KAKAO_TEXT_SEND_TAG, message)
            error(message)
        }

    private fun hasCommittedRow(
        roomId: Long,
        message: String,
        minimumCreatedAt: Long,
        minimumRowId: Long,
        rawAttachment: String?,
    ): Boolean {
        val db =
            SQLiteDatabase.openDatabase(
                databasePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        db.use { database ->
            if (rawAttachment != null) {
                return findCommittedKakaoLinkChatLog(database, roomId, minimumCreatedAt, minimumRowId, rawAttachment) != null
            }
            return findCommittedChatLogMessage(database, roomId, message, minimumCreatedAt) != null
        }
    }

    private fun sleepBeforeRetry(
        requestId: String?,
        roomId: Long,
    ): Boolean {
        try {
            sleeper(COMMIT_RETRY_DELAY_MS)
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logInfo(KAKAO_TEXT_SEND_TAG, "kakaolink commit check interrupted requestId=$requestId room=$roomId")
            return false
        }
    }

    private companion object {
        private const val COMMIT_ATTEMPTS = 12
        private const val COMMIT_RETRY_DELAY_MS = 250L
    }
}
