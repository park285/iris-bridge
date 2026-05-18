package party.qwer.iris.imagebridge.runtime.send

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

internal data class KakaoLeveragePatchTarget(
    val rowId: Long,
    val userId: Long,
    val encType: Int,
    val committedAttachment: String? = null,
)

internal fun findLeverageAttachmentPatchTarget(
    database: SQLiteDatabase,
    roomId: Long,
    message: String,
    minimumCreatedAt: Long,
): KakaoLeveragePatchTarget? =
    database
        .rawQuery(
            """
            select _id,user_id,message,v
            from chat_logs
            where chat_id=? and type=71 and created_at>=?
            order by _id desc
            limit 5
            """.trimIndent(),
            arrayOf(roomId.toString(), minimumCreatedAt.toString()),
        ).use { cursor ->
            findMatchingLeverageRow(cursor, message)
        }

internal fun findCommittedChatLogMessage(
    database: SQLiteDatabase,
    roomId: Long,
    message: String,
    minimumCreatedAt: Long,
): KakaoLeveragePatchTarget? =
    database
        .rawQuery(
            """
            select _id,user_id,message,v
            from chat_logs
            where chat_id=? and created_at>=?
            order by _id desc
            limit 10
            """.trimIndent(),
            arrayOf(roomId.toString(), minimumCreatedAt.toString()),
        ).use { cursor ->
            findMatchingLeverageRow(cursor, message)
        }

internal fun findCommittedKakaoLinkChatLog(
    database: SQLiteDatabase,
    roomId: Long,
    minimumCreatedAt: Long,
    minimumRowId: Long,
    rawAttachment: String,
): KakaoLeveragePatchTarget? =
    database
        .rawQuery(
            """
            select _id,user_id,attachment,v
            from chat_logs
            where chat_id=? and type=71 and created_at>=? and _id>?
            order by _id desc
            limit 10
            """.trimIndent(),
            arrayOf(roomId.toString(), minimumCreatedAt.toString(), minimumRowId.toString()),
        ).use { cursor ->
            findMatchingKakaoLinkRow(cursor, rawAttachment)
        }

private fun findMatchingKakaoLinkRow(
    cursor: Cursor,
    rawAttachment: String,
): KakaoLeveragePatchTarget? {
    while (cursor.moveToNext()) {
        val rowId = cursor.getLong(0)
        val userId = cursor.getLong(1)
        val encryptedAttachment = cursor.getString(2)
        val encType = leverageEncryptionType(cursor.getString(3))
        val attachment = decryptedLeverageMessage(encryptedAttachment, encType, userId, rawAttachment)
        if (attachment != null && kakaoLinkAttachmentsMatch(rawAttachment, attachment)) {
            return KakaoLeveragePatchTarget(rowId = rowId, userId = userId, encType = encType, committedAttachment = attachment)
        }
    }
    return null
}

internal fun kakaoLinkAttachmentsMatch(
    expectedRawAttachment: String,
    committedRawAttachment: String,
): Boolean =
    runCatching {
        val expected = JSONObject(expectedRawAttachment)
        val committed = JSONObject(committedRawAttachment)
        if (templateId(expected) != templateId(committed)) {
            return@runCatching false
        }
        val expectedTitles = itemTitles(expected)
        val committedTitles = itemTitles(committed)
        expectedTitles.isEmpty() || expectedTitles == committedTitles
    }.getOrDefault(false)

private fun templateId(attachment: JSONObject): String? =
    firstNonBlankPatchValue(
        attachment.optString("template_id"),
        attachment.optString("templateId"),
        attachment.optJSONObject("K")?.optString("ti"),
        attachment.optJSONObject("P")?.optString("SDID"),
        attachment.optString("ti"),
    )

private fun itemTitles(attachment: JSONObject): List<String> {
    val items = attachment.optJSONObject("C")?.optJSONArray("ITL") ?: return emptyList()
    val contentTitles =
        (0 until items.length())
            .mapNotNull { index ->
                items
                    .optJSONObject(index)
                    ?.optJSONObject("TD")
                    ?.optString("T")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
    if (contentTitles.isNotEmpty()) {
        return contentTitles
    }
    val args = attachment.optJSONObject("template_args") ?: attachment.optJSONObject("ta") ?: return emptyList()
    return (1..items.length())
        .mapNotNull { index ->
            args.optString("item${index}_title").trim().takeIf { it.isNotEmpty() }
        }
}

private fun findMatchingLeverageRow(
    cursor: Cursor,
    message: String,
): KakaoLeveragePatchTarget? {
    while (cursor.moveToNext()) {
        val rowId = cursor.getLong(0)
        val userId = cursor.getLong(1)
        val encryptedMessage = cursor.getString(2)
        val encType = leverageEncryptionType(cursor.getString(3))
        val rowMessage = decryptedLeverageMessage(encryptedMessage, encType, userId, message)
        if (rowMessage == message) return KakaoLeveragePatchTarget(rowId, userId, encType)
    }
    return null
}

private fun decryptedLeverageMessage(
    encryptedMessage: String,
    encType: Int,
    userId: Long,
    expectedPlaintext: String,
): String? {
    if (encryptedMessage == expectedPlaintext) return encryptedMessage
    return runCatching {
        KakaoChatLogAttachmentCrypto.decrypt(encType, encryptedMessage, userId)
    }.getOrNull()
}

private fun leverageEncryptionType(value: String): Int =
    ENCRYPTION_TYPE_REGEX
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: DEFAULT_ENCRYPTION_TYPE

private const val DEFAULT_ENCRYPTION_TYPE = 31
private val ENCRYPTION_TYPE_REGEX = Regex(""""enc"\s*:\s*(\d+)""")

private fun firstNonBlankPatchValue(vararg values: String?): String? = values.firstOrNull { value -> !value.isNullOrBlank() }
