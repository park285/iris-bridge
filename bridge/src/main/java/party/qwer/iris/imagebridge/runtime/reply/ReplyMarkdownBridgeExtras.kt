package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent
import org.json.JSONObject
import party.qwer.iris.ReplyHookSignatureProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.replyMarkdownPendingContextJson
import party.qwer.iris.resolveBridgeToken

internal object ReplyMarkdownBridgeExtras {
    const val SESSION_ID = "party.qwer.iris.extra.SHARE_SESSION_ID"
    const val THREAD_ID = "party.qwer.iris.extra.THREAD_ID"
    const val THREAD_SCOPE = "party.qwer.iris.extra.THREAD_SCOPE"
    const val ROOM_ID = "party.qwer.iris.extra.ROOM_ID"
    const val CREATED_AT = "party.qwer.iris.extra.CREATED_AT"
    const val SIGNATURE = ReplyHookSignatureProtocol.EXTRA_SIGNATURE

    private const val EXTRA_SEND_INTENT = "ConnectManager.ACTION_SEND_INTENT"
    private const val EXTRA_CHAT_MESSAGE = "EXTRA_CHAT_MESSAGE"

    internal data class Snapshot(
        val sessionId: String? = null,
        val roomIdRaw: String? = null,
        val fallbackRoomId: Long? = null,
        val threadIdRaw: String? = null,
        val threadScope: Int? = null,
        val createdAtEpochMs: Long? = null,
        val signature: String? = null,
        val messageText: String? = null,
        val nestedMessageText: String? = null,
    )

    fun extractPendingContext(
        intent: Intent?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ReplyMarkdownPendingContext? =
        intent?.let { source ->
            extractPendingContext(
                Snapshot(
                    sessionId = source.stringExtraOrNull(SESSION_ID),
                    roomIdRaw = source.stringExtraOrNull(ROOM_ID),
                    fallbackRoomId = source.longExtraOrNull("key_id"),
                    threadIdRaw = source.stringExtraOrNull(THREAD_ID),
                    threadScope = source.intExtraOrNull(THREAD_SCOPE),
                    createdAtEpochMs = source.longExtraOrNull(CREATED_AT),
                    signature = source.stringExtraOrNull(SIGNATURE),
                    messageText = source.textExtraOrNull(Intent.EXTRA_TEXT),
                    nestedMessageText = source.nestedIntentExtraOrNull(EXTRA_SEND_INTENT)?.textExtraOrNull(EXTRA_CHAT_MESSAGE),
                ),
                nowEpochMs = nowEpochMs,
            )
        }

    fun extractPendingContext(
        snapshot: Snapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
        bridgeToken: String = resolveBridgeToken(),
    ): ReplyMarkdownPendingContext? {
        val context =
            BridgeCore.replyMarkdownPendingContextJson(
                pendingContextRequestJson(snapshot, nowEpochMs, bridgeToken),
            ) ?: return null
        return ReplyMarkdownPendingContext(
            roomId = context.getLong("roomId"),
            messageText = context.getString("messageText"),
            threadId = context.getLong("threadId"),
            threadScope = context.getInt("threadScope"),
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
                    .putIfNotNull("threadIdRaw", snapshot.threadIdRaw)
                    .putIfNotNull("threadScope", snapshot.threadScope)
                    .putIfNotNull("createdAtEpochMs", snapshot.createdAtEpochMs)
                    .putIfNotNull("signature", snapshot.signature)
                    .putIfNotNull("messageText", snapshot.messageText)
                    .putIfNotNull("nestedMessageText", snapshot.nestedMessageText),
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
