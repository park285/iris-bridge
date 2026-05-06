package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent

internal data class ReplyMarkdownPendingContext(
    val roomId: Long,
    val messageText: String,
    val threadId: Long,
    val threadScope: Int,
    val sessionId: String? = null,
    val createdAtEpochMs: Long,
)

internal class ReplyMarkdownPendingContextStore(
    private val ttlMs: Long = 10 * 60_000L,
    private val maxEntries: Int = 256,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val context: ReplyMarkdownPendingContext,
        val rememberedAtEpochMs: Long,
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun remember(context: ReplyMarkdownPendingContext) {
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
    ): ReplyMarkdownPendingContext? {
        pruneExpiredLocked()
        if (sessionId != null) {
            findAndRemoveBySessionIdLocked(roomId, sessionId)?.let { return it }
            return findAndRemoveLatestByMessageLocked(roomId, messageText, sessionlessOnly = true)
        }
        return findAndRemoveLatestByMessageLocked(roomId, messageText, sessionlessOnly = false)
    }

    private fun findAndRemoveBySessionIdLocked(
        roomId: Long,
        sessionId: String,
    ): ReplyMarkdownPendingContext? {
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
    ): ReplyMarkdownPendingContext? {
        val iterator = entries.iterator()
        var latest: ReplyMarkdownPendingContext? = null
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

    @Synchronized
    fun size(): Int {
        pruneExpiredLocked()
        return entries.size
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

internal object ReplyMarkdownIngressCapture {
    fun capture(
        intent: Intent?,
        store: ReplyMarkdownPendingContextStore,
        onCaptured: (ReplyMarkdownPendingContext) -> Unit = {},
    ): ReplyMarkdownPendingContext? {
        val context = ReplyMarkdownBridgeExtras.extractPendingContext(intent) ?: return null
        store.remember(context)
        onCaptured(context)
        return context
    }
}
