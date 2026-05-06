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
