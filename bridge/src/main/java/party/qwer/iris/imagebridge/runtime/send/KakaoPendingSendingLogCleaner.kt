package party.qwer.iris.imagebridge.runtime.send

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

internal class KakaoPendingSendingLogCleaner(
    private val databasePath: String,
) {
    fun cleanupMatchedPendingSendingLogs(
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
            val rowIds = findMatchedPendingRowIds(database, roomId, minimumCreatedAt, rawAttachment)
            return rowIds.sumOf { rowId ->
                database.delete("chat_sending_logs", "_id=?", arrayOf(rowId.toString()))
            }
        }
    }

    private fun findMatchedPendingRowIds(
        database: SQLiteDatabase,
        roomId: Long,
        minimumCreatedAt: Long,
        rawAttachment: String,
    ): List<Long> =
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
                collectMatchedPendingRowIds(cursor, rawAttachment)
            }

    private fun collectMatchedPendingRowIds(
        cursor: Cursor,
        rawAttachment: String,
    ): List<Long> {
        val rowIds = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val attachment = cursor.getString(1)
            if (attachmentMatches(rawAttachment, attachment)) {
                rowIds += cursor.getLong(0)
            }
        }
        return rowIds
    }

    private fun attachmentMatches(
        expectedAttachment: String,
        pendingAttachment: String?,
    ): Boolean =
        !pendingAttachment.isNullOrBlank() &&
            kakaoLinkPendingCleanupAttachmentsMatch(expectedAttachment, pendingAttachment)
}
