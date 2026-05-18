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

    fun cleanupPendingKakaoLinkSendingLogs(
        roomId: Long,
        minimumCreatedAt: Long,
        requestId: String?,
        rawAttachment: String,
    ): Boolean = true
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

    override fun cleanupPendingKakaoLinkSendingLogs(
        roomId: Long,
        minimumCreatedAt: Long,
        requestId: String?,
        rawAttachment: String,
    ): Boolean {
        repeat(CLEANUP_ATTEMPTS) { attempt ->
            runCatching {
                cleanupMatchedPendingSendingLogs(roomId, minimumCreatedAt, rawAttachment)
            }.onSuccess { deleted ->
                if (deleted > 0) {
                    logInfo(KAKAO_TEXT_SEND_TAG, "kakaolink pending sending logs cleaned requestId=$requestId room=$roomId count=$deleted")
                }
                return true
            }.onFailure { error ->
                logInfo(
                    KAKAO_TEXT_SEND_TAG,
                    "kakaolink pending sending log cleanup failed requestId=$requestId room=$roomId " +
                        "attempt=${attempt + 1} error=${error.javaClass.name}: ${error.message}",
                )
                if (attempt + 1 < CLEANUP_ATTEMPTS && !sleepBeforeCleanupRetry(requestId, roomId)) {
                    return false
                }
            }
        }
        return false
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

    private fun cleanupMatchedPendingSendingLogs(
        roomId: Long,
        minimumCreatedAt: Long,
        rawAttachment: String,
    ): Int {
        val db =
            SQLiteDatabase.openDatabase(
                databasePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
        db.use { database ->
            val rowIds = mutableListOf<Long>()
            database
                .rawQuery(
                    """
                    select _id,attachment
                    from chat_sending_logs
                    where chat_id=? and type=71 and created_at>=?
                    order by _id desc
                    limit 10
                    """.trimIndent(),
                    arrayOf(roomId.toString(), minimumCreatedAt.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val rowId = cursor.getLong(0)
                        val attachment = cursor.getString(1)
                        if (!attachment.isNullOrBlank() && kakaoLinkPendingCleanupAttachmentsMatch(rawAttachment, attachment)) {
                            rowIds += rowId
                        }
                    }
                }
            return rowIds.sumOf { rowId ->
                database.delete("chat_sending_logs", "_id=?", arrayOf(rowId.toString()))
            }
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

    private fun sleepBeforeCleanupRetry(
        requestId: String?,
        roomId: Long,
    ): Boolean {
        try {
            sleeper(CLEANUP_RETRY_DELAY_MS)
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logInfo(KAKAO_TEXT_SEND_TAG, "kakaolink pending sending log cleanup interrupted requestId=$requestId room=$roomId")
            return false
        }
    }

    private companion object {
        private const val COMMIT_ATTEMPTS = 12
        private const val COMMIT_RETRY_DELAY_MS = 250L
        private const val CLEANUP_ATTEMPTS = 3
        private const val CLEANUP_RETRY_DELAY_MS = 100L
    }
}
