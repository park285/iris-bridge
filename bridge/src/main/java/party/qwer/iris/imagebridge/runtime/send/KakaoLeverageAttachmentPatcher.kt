package party.qwer.iris.imagebridge.runtime.send

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal interface KakaoLeverageAttachmentPatcher {
    fun patchAsync(
        roomId: Long,
        message: String,
        rawAttachment: String,
        requestId: String?,
    )
}

internal class KakaoLeverageAttachmentDbPatcher(
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
    private val databasePath: String = KAKAO_TALK_DATABASE_PATH,
    private val clock: () -> Long = System::currentTimeMillis,
    private val executor: Executor = newPatchExecutor(logInfo),
    private val sleeper: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
    private val databaseOpener: (Int) -> SQLiteDatabase = { flags ->
        SQLiteDatabase.openDatabase(
            databasePath,
            null,
            flags,
        )
    },
) : KakaoLeverageAttachmentPatcher {
    override fun patchAsync(
        roomId: Long,
        message: String,
        rawAttachment: String,
        requestId: String?,
    ) {
        val minimumCreatedAt = clock() / 1000L - PATCH_CREATED_AT_GRACE_SECONDS
        try {
            executor.execute {
                patchWithRetry(roomId, message, rawAttachment, requestId, minimumCreatedAt)
            }
        } catch (error: RejectedExecutionException) {
            logInfo(
                KAKAO_TEXT_SEND_TAG,
                "leverage attachment patch queue full requestId=$requestId room=$roomId error=${error.javaClass.name}",
            )
        }
    }

    private fun patchWithRetry(
        roomId: Long,
        message: String,
        rawAttachment: String,
        requestId: String?,
        minimumCreatedAt: Long,
    ) {
        var db: SQLiteDatabase? = null
        try {
            repeat(PATCH_ATTEMPTS) { attempt ->
                if (!sleepBeforeRetry(requestId, roomId)) return
                val patched =
                    runCatching {
                        val database = db ?: databaseOpener(SQLiteDatabase.OPEN_READWRITE)
                        if (db == null) db = database
                        patchLatest(database, roomId, message, rawAttachment, minimumCreatedAt)
                    }.onFailure { error ->
                        val failedDb = db
                        db = null
                        failedDb?.close()
                        logInfo(
                            KAKAO_TEXT_SEND_TAG,
                            "leverage attachment patch failed requestId=$requestId room=$roomId " +
                                "attempt=${attempt + 1} error=${error.javaClass.name}: ${error.message}",
                        )
                    }.getOrDefault(false)
                if (patched) {
                    logInfo(
                        KAKAO_TEXT_SEND_TAG,
                        "leverage attachment patched requestId=$requestId room=$roomId attempt=${attempt + 1}",
                    )
                    return
                }
            }
        } finally {
            val openDb = db
            db = null
            openDb?.close()
        }
        logInfo(KAKAO_TEXT_SEND_TAG, "leverage attachment patch row not found requestId=$requestId room=$roomId")
    }

    private fun sleepBeforeRetry(
        requestId: String?,
        roomId: Long,
    ): Boolean {
        try {
            sleeper(PATCH_RETRY_DELAY_MS)
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logInfo(KAKAO_TEXT_SEND_TAG, "leverage attachment patch interrupted requestId=$requestId room=$roomId")
            return false
        }
    }

    private fun patchLatest(
        database: SQLiteDatabase,
        roomId: Long,
        message: String,
        rawAttachment: String,
        minimumCreatedAt: Long,
    ): Boolean {
        val row =
            kakaoLinkSpecPatchMatchAttachments(rawAttachment)
                .asSequence()
                .mapNotNull { candidateAttachment ->
                    findCommittedKakaoLinkChatLog(
                        database = database,
                        roomId = roomId,
                        minimumCreatedAt = minimumCreatedAt,
                        minimumRowId = 0L,
                        rawAttachment = candidateAttachment,
                    )
                }.firstOrNull()
                ?: findLeverageAttachmentPatchTarget(database, roomId, message, minimumCreatedAt)
                ?: return false
        return updateAttachment(database, row, kakaoLinkDisplayPatchAttachment(row.committedAttachment, rawAttachment))
    }

    private fun updateAttachment(
        database: SQLiteDatabase,
        row: KakaoLeveragePatchTarget,
        rawAttachment: String,
    ): Boolean {
        val encryptedAttachment = KakaoChatLogAttachmentCrypto.encrypt(row.encType, rawAttachment, row.userId)
        val values =
            ContentValues().apply {
                put("attachment", encryptedAttachment)
            }
        return database.update("chat_logs", values, "_id=?", arrayOf(row.rowId.toString())) == 1
    }

    private companion object {
        private const val PATCH_ATTEMPTS = 12
        private const val PATCH_RETRY_DELAY_MS = 250L
        private const val PATCH_CREATED_AT_GRACE_SECONDS = 5L
        private const val PATCH_QUEUE_CAPACITY = 32

        private fun newPatchExecutor(logInfo: (String, String) -> Unit): Executor =
            ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(PATCH_QUEUE_CAPACITY),
                ThreadFactory { runnable ->
                    Thread(runnable, "iris-leverage-attachment-patch").apply {
                        isDaemon = true
                    }
                },
            ) { _, executor ->
                logInfo(
                    KAKAO_TEXT_SEND_TAG,
                    "leverage attachment patch queue full active=${executor.activeCount} queued=${executor.queue.size}",
                )
            }
    }
}
