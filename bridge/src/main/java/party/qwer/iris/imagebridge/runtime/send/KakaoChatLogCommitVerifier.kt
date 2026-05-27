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
    private val databaseOpener: (Int) -> SQLiteDatabase = { flags ->
        SQLiteDatabase.openDatabase(
            databasePath,
            null,
            flags,
        )
    },
) : KakaoChatLogCommitVerifier {
    private val pendingCleaner = KakaoPendingSendingLogCleaner(databaseOpener)

    override fun awaitCommitted(
        roomId: Long,
        message: String,
        minimumCreatedAt: Long,
        minimumRowId: Long,
        requestId: String?,
        rawAttachment: String?,
    ): Boolean {
        var db: SQLiteDatabase? = null
        try {
            repeat(COMMIT_ATTEMPTS) { attempt ->
                if (!sleepBeforeKakaoLinkRetry(sleeper, COMMIT_RETRY_DELAY_MS, requestId, roomId, "kakaolink commit check interrupted", logInfo)) {
                    return false
                }
                val committed =
                    runCatching {
                        val database = db ?: databaseOpener(SQLiteDatabase.OPEN_READONLY).also { opened -> db = opened }
                        hasCommittedRow(database, roomId, message, minimumCreatedAt, minimumRowId, rawAttachment)
                    }.onFailure { error ->
                        db.closeAndClear { db = null }
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
        } finally {
            db.closeAndClear { db = null }
        }
        logInfo(KAKAO_TEXT_SEND_TAG, "kakaolink commit not found requestId=$requestId room=$roomId minimumRowId=$minimumRowId")
        return false
    }

    override fun latestCommittedRowId(roomId: Long): Long =
        runCatching {
            val db = databaseOpener(SQLiteDatabase.OPEN_READONLY)
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
                pendingCleaner.cleanupMatchedPendingSendingLogs(roomId, minimumCreatedAt, rawAttachment)
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
                if (
                    attempt + 1 < CLEANUP_ATTEMPTS &&
                    !sleepBeforeKakaoLinkRetry(
                        sleeper,
                        CLEANUP_RETRY_DELAY_MS,
                        requestId,
                        roomId,
                        "kakaolink pending sending log cleanup interrupted",
                        logInfo,
                    )
                ) {
                    return false
                }
            }
        }
        return false
    }

    private fun hasCommittedRow(
        database: SQLiteDatabase,
        roomId: Long,
        message: String,
        minimumCreatedAt: Long,
        minimumRowId: Long,
        rawAttachment: String?,
    ): Boolean {
        if (rawAttachment != null) {
            return findCommittedKakaoLinkChatLog(database, roomId, minimumCreatedAt, minimumRowId, rawAttachment) != null
        }
        return findCommittedChatLogMessage(database, roomId, message, minimumCreatedAt) != null
    }

    private companion object {
        private const val COMMIT_ATTEMPTS = 12
        private const val COMMIT_RETRY_DELAY_MS = 250L
        private const val CLEANUP_ATTEMPTS = 3
        private const val CLEANUP_RETRY_DELAY_MS = 100L
    }
}

private fun SQLiteDatabase?.closeAndClear(clear: () -> Unit) {
    try {
        this?.close()
    } finally {
        clear()
    }
}
