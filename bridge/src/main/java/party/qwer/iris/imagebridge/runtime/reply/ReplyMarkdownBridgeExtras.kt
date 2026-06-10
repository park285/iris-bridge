package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent
import party.qwer.iris.ReplyHookSignatureProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
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
        val threadId = snapshot.threadIdRaw?.toLongOrNull() ?: return null
        val threadScope = snapshot.threadScope ?: 2
        if (threadScope <= 0) return null
        val roomId = snapshot.roomIdRaw?.toLongOrNull() ?: snapshot.fallbackRoomId ?: return null
        val messageText = snapshot.messageText ?: snapshot.nestedMessageText ?: return null
        if (messageText.isBlank()) return null
        if (
            !BridgeCore.replyHookVerify(
                bridgeToken = bridgeToken,
                roomId = roomId,
                messageText = messageText,
                sessionId = snapshot.sessionId,
                createdAtEpochMs = snapshot.createdAtEpochMs,
                mentionsHash = null,
                signature = snapshot.signature,
                nowEpochMs = nowEpochMs,
            )
        ) {
            return null
        }
        return ReplyMarkdownPendingContext(
            roomId = roomId,
            messageText = messageText,
            threadId = threadId,
            threadScope = threadScope,
            sessionId = snapshot.sessionId,
            createdAtEpochMs = snapshot.createdAtEpochMs ?: nowEpochMs,
        )
    }
}
