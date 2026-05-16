package party.qwer.iris.imagebridge.runtime.reply

internal data class ReplyLeveragePendingContext(
    val roomId: Long,
    val messageText: String,
    val attachmentText: String,
    val threadId: Long?,
    val threadScope: Int?,
    val sessionId: String? = null,
    val createdAtEpochMs: Long,
)

internal class ReplyLeveragePendingContextStore(
    private val ttlMs: Long = 10 * 60_000L,
    private val maxEntries: Int = 256,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val context: ReplyLeveragePendingContext,
        val rememberedAtEpochMs: Long,
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun remember(context: ReplyLeveragePendingContext) {
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
    ): ReplyLeveragePendingContext? {
        pruneExpiredLocked()
        if (sessionId != null) {
            findAndRemoveBySessionIdLocked(roomId, sessionId)?.let { return it }
        }
        return findAndRemoveLatestByMessageLocked(roomId, messageText)
    }

    @Synchronized
    fun matchLatest(
        roomId: Long,
        sessionId: String? = null,
    ): ReplyLeveragePendingContext? {
        pruneExpiredLocked()
        if (sessionId != null) {
            findAndRemoveBySessionIdLocked(roomId, sessionId)?.let { return it }
        }
        return findAndRemoveLatestByRoomLocked(roomId)
    }

    private fun findAndRemoveBySessionIdLocked(
        roomId: Long,
        sessionId: String,
    ): ReplyLeveragePendingContext? {
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
    ): ReplyLeveragePendingContext? {
        val iterator = entries.iterator()
        var latest: ReplyLeveragePendingContext? = null
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.context.roomId == roomId && entry.context.messageText == messageText) {
                latest = entry.context
                iterator.remove()
            }
        }
        return latest
    }

    private fun findAndRemoveLatestByRoomLocked(roomId: Long): ReplyLeveragePendingContext? {
        val iterator = entries.iterator()
        var latest: ReplyLeveragePendingContext? = null
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.context.roomId == roomId) {
                latest = entry.context
                iterator.remove()
            }
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
