package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
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
        val roomId = snapshot.roomIdRaw?.toLongOrNull() ?: snapshot.fallbackRoomId ?: return null
        val sessionId = snapshot.sessionId?.takeIf { it.isNotBlank() } ?: return null
        val messageText = snapshot.messageText ?: snapshot.nestedMessageText ?: return null
        if (messageText.isBlank()) return null
        val attachmentText =
            listOfNotNull(snapshot.attachmentText, snapshot.nestedAttachmentText)
                .firstNotNullOfOrNull(ReplyMentionSendingLogAccess::mentionAttachmentOrNull)
                ?: return null
        val mentionsHash = BridgeCore.mentionsHashFromAttachment(attachmentText) ?: return null
        if (
            !BridgeCore.replyHookVerify(
                bridgeToken = bridgeToken,
                roomId = roomId,
                messageText = messageText,
                sessionId = sessionId,
                createdAtEpochMs = snapshot.createdAtEpochMs,
                mentionsHash = mentionsHash,
                signature = snapshot.signature,
                nowEpochMs = nowEpochMs,
            )
        ) {
            return null
        }
        return ReplyMentionPendingContext(
            roomId = roomId,
            messageText = messageText,
            attachmentText = attachmentText,
            sessionId = sessionId,
            createdAtEpochMs = snapshot.createdAtEpochMs ?: nowEpochMs,
        )
    }
}
