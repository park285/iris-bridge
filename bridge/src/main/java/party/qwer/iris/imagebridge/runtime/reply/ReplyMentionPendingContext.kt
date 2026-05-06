package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent

internal data class ReplyMentionPendingContext(
    val roomId: Long,
    val messageText: String,
    val attachmentText: String,
    val sessionId: String? = null,
    val createdAtEpochMs: Long,
)

internal class ReplyMentionPendingContextStore(
    private val ttlMs: Long = 10 * 60_000L,
    private val maxEntries: Int = 256,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val context: ReplyMentionPendingContext,
        val rememberedAtEpochMs: Long,
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun remember(context: ReplyMentionPendingContext) {
        pruneExpiredLocked()
        while (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(Entry(context, clock()))
    }

    @Synchronized
    fun match(
        roomId: Long,
        messageText: String,
        sessionId: String? = null,
    ): ReplyMentionPendingContext? {
        pruneExpiredLocked()
        if (sessionId != null) {
            findAndRemoveBySessionIdLocked(roomId, sessionId)?.let { return it }
            return findAndRemoveLatestByMessageLocked(roomId, messageText, sessionlessOnly = true)
        }
        return findAndRemoveLatestByMessageLocked(roomId, messageText, sessionlessOnly = false)
    }

    @Synchronized
    fun size(): Int {
        pruneExpiredLocked()
        return entries.size
    }

    private fun findAndRemoveBySessionIdLocked(
        roomId: Long,
        sessionId: String,
    ): ReplyMentionPendingContext? {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.context.roomId == roomId && entry.context.sessionId == sessionId) {
                iterator.remove()
                return entry.context
            }
        }
        return null
    }

    private fun findAndRemoveLatestByMessageLocked(
        roomId: Long,
        messageText: String,
        sessionlessOnly: Boolean,
    ): ReplyMentionPendingContext? {
        val iterator = entries.iterator()
        var latest: ReplyMentionPendingContext? = null
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.context.roomId != roomId || entry.context.messageText != messageText) {
                continue
            }
            if (sessionlessOnly && entry.context.sessionId != null) {
                continue
            }
            latest = entry.context
            iterator.remove()
        }
        return latest
    }

    private fun pruneExpiredLocked() {
        val now = clock()
        while (entries.isNotEmpty()) {
            val oldest = entries.first()
            if (now - oldest.rememberedAtEpochMs <= ttlMs) {
                return
            }
            entries.removeFirst()
        }
    }
}

internal object ReplyMentionIngressCapture {
    fun capture(
        intent: Intent?,
        store: ReplyMentionPendingContextStore,
        onCaptured: (ReplyMentionPendingContext) -> Unit = {},
    ): ReplyMentionPendingContext? {
        val context = ReplyMentionBridgeExtras.extractPendingContext(intent) ?: return null
        store.remember(context)
        onCaptured(context)
        return context
    }
}

internal object ReplyMentionBridgeExtras {
    private const val EXTRA_SEND_INTENT = "ConnectManager.ACTION_SEND_INTENT"
    private const val EXTRA_CHAT_MESSAGE = "EXTRA_CHAT_MESSAGE"
    private const val EXTRA_CHAT_ATTACHMENT = "EXTRA_CHAT_ATTACHMENT"

    internal data class Snapshot(
        val sessionId: String? = null,
        val roomIdRaw: String? = null,
        val fallbackRoomId: Long? = null,
        val createdAtEpochMs: Long? = null,
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
    ): ReplyMentionPendingContext? {
        val roomId = snapshot.roomIdRaw?.toLongOrNull() ?: snapshot.fallbackRoomId ?: return null
        val messageText = snapshot.messageText ?: snapshot.nestedMessageText ?: return null
        if (messageText.isBlank()) {
            return null
        }

        val attachmentText =
            listOfNotNull(snapshot.attachmentText, snapshot.nestedAttachmentText)
                .firstNotNullOfOrNull(ReplyMentionSendingLogAccess::mentionAttachmentOrNull)
                ?: return null

        return ReplyMentionPendingContext(
            roomId = roomId,
            messageText = messageText,
            attachmentText = attachmentText,
            sessionId = snapshot.sessionId,
            createdAtEpochMs = snapshot.createdAtEpochMs ?: nowEpochMs,
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.stringExtraOrNull(name: String): String? =
        when (val raw = extras?.get(name)) {
            is String -> raw.takeIf { it.isNotBlank() }
            is CharSequence -> raw.toString().takeIf { it.isNotBlank() }
            is Number -> raw.toString()
            else -> null
        }

    @Suppress("DEPRECATION")
    private fun Intent.textExtraOrNull(name: String): String? =
        when (val raw = extras?.get(name)) {
            is CharSequence -> raw.toString()
            is Number -> raw.toString()
            else -> null
        }

    @Suppress("DEPRECATION")
    private fun Intent.longExtraOrNull(name: String): Long? =
        when (val raw = extras?.get(name)) {
            is Long -> raw
            is Int -> raw.toLong()
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }

    private fun Intent.nestedIntentExtraOrNull(
        name: String,
    ): Intent? =
        runCatching {
            getParcelableExtra(name, Intent::class.java)
        }.getOrNull()
}
