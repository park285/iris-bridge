package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent
import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.replyMentionPendingContextJson
import party.qwer.iris.resolveBridgeToken

internal object ReplyMentionBridgeExtras {
    private const val EXTRA_SEND_INTENT = "ConnectManager.ACTION_SEND_INTENT"
    private const val EXTRA_CHAT_MESSAGE = "EXTRA_CHAT_MESSAGE"
    private const val EXTRA_CHAT_ATTACHMENT = "EXTRA_CHAT_ATTACHMENT"

    internal data class Snapshot(
        val sessionId: String? = null,
        val roomIdRaw: String? = null,
        val fallbackRoomId: Long? = null,
        val createdAtEpochMs: Long? = null,
        val signature: String? = null,
        val messageText: String? = null,
        val nestedMessageText: String? = null,
        val attachmentText: String? = null,
        val nestedAttachmentText: String? = null,
    )

    fun extractPendingContext(
        intent: Intent?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ReplyMentionPendingContext? =
        intent?.let { source ->
            extractPendingContext(
                Snapshot(
                    sessionId = source.stringExtraOrNull(ReplyMarkdownBridgeExtras.SESSION_ID),
                    roomIdRaw = source.stringExtraOrNull(ReplyMarkdownBridgeExtras.ROOM_ID),
                    fallbackRoomId = source.longExtraOrNull("key_id"),
                    createdAtEpochMs = source.longExtraOrNull(ReplyMarkdownBridgeExtras.CREATED_AT),
                    signature = source.stringExtraOrNull(ReplyMarkdownBridgeExtras.SIGNATURE),
                    messageText = source.textExtraOrNull(Intent.EXTRA_TEXT),
                    nestedMessageText = source.nestedIntentExtraOrNull(EXTRA_SEND_INTENT)?.textExtraOrNull(EXTRA_CHAT_MESSAGE),
                    attachmentText = source.textExtraOrNull(EXTRA_CHAT_ATTACHMENT),
                    nestedAttachmentText = source.nestedIntentExtraOrNull(EXTRA_SEND_INTENT)?.textExtraOrNull(EXTRA_CHAT_ATTACHMENT),
                ),
                nowEpochMs = nowEpochMs,
            )
        }

    fun extractPendingContext(
        snapshot: Snapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
        bridgeToken: String = resolveBridgeToken(),
    ): ReplyMentionPendingContext? {
        val context =
            BridgeCore.replyMentionPendingContextJson(
                pendingContextRequestJson(snapshot, nowEpochMs, bridgeToken),
            ) ?: return null
        return ReplyMentionPendingContext(
            roomId = context.getLong("roomId"),
            messageText = context.getString("messageText"),
            attachmentText = context.getString("attachmentText"),
            sessionId = context.stringOrNull("sessionId"),
            createdAtEpochMs = context.getLong("createdAtEpochMs"),
        )
    }

    private fun pendingContextRequestJson(
        snapshot: Snapshot,
        nowEpochMs: Long,
        bridgeToken: String,
    ): String =
        JSONObject()
            .put("bridgeToken", bridgeToken)
            .put("nowEpochMs", nowEpochMs)
            .put(
                "snapshot",
                JSONObject()
                    .putIfNotNull("sessionId", snapshot.sessionId)
                    .putIfNotNull("roomIdRaw", snapshot.roomIdRaw)
                    .putIfNotNull("fallbackRoomId", snapshot.fallbackRoomId)
                    .putIfNotNull("createdAtEpochMs", snapshot.createdAtEpochMs)
                    .putIfNotNull("signature", snapshot.signature)
                    .putIfNotNull("messageText", snapshot.messageText)
                    .putIfNotNull("nestedMessageText", snapshot.nestedMessageText)
                    .putIfNotNull("attachmentText", snapshot.attachmentText)
                    .putIfNotNull("nestedAttachmentText", snapshot.nestedAttachmentText),
            ).toString()
}

private fun JSONObject.putIfNotNull(
    key: String,
    value: Any?,
): JSONObject =
    apply {
        if (value != null) put(key, value)
    }

private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
