package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class BridgeRequestDeduper(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val onDedupeHit: (String) -> Unit = {},
) {
    private data class Entry(
        val createdAtMs: Long,
        val response: CompletableFuture<ImageBridgeProtocol.ImageBridgeResponse>,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun execute(
        key: String,
        block: () -> ImageBridgeProtocol.ImageBridgeResponse,
    ): ImageBridgeProtocol.ImageBridgeResponse {
        pruneExpired()
        val future = CompletableFuture<ImageBridgeProtocol.ImageBridgeResponse>()
        val entry = Entry(createdAtMs = nowMs(), response = future)
        val existing = entries.putIfAbsent(key, entry)
        if (existing != null) {
            onDedupeHit(key)
            return existing.response.get(DEDUPE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }

        return try {
            val response = block()
            future.complete(response)
            response
        } catch (error: Throwable) {
            val response =
                bridgeFailureResponse(
                    error = error.message ?: "internal error",
                    errorCode = ImageBridgeProtocol.ERROR_INTERNAL,
                )
            future.complete(response)
            throw error
        } finally {
            pruneOversize()
        }
    }

    private fun pruneExpired() {
        val cutoff = nowMs() - ttlMs
        entries.entries.removeIf { (_, entry) -> entry.createdAtMs < cutoff }
    }

    private fun pruneOversize() {
        if (entries.size <= maxEntries) return
        val victims =
            entries.entries
                .sortedBy { it.value.createdAtMs }
                .take((entries.size - maxEntries).coerceAtLeast(0))
                .map { it.key }
        victims.forEach(entries::remove)
    }

    private companion object {
        private const val DEFAULT_TTL_MS = 10 * 60 * 1000L
        private const val DEFAULT_MAX_ENTRIES = 4096
        private const val DEDUPE_WAIT_TIMEOUT_MS = 30_000L
    }
}
