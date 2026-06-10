package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.DedupeState
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class BridgeRequestDeduper private constructor(
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
    private val ttlMs: Long,
    private val maxEntries: Int,
    private val nowMs: () -> Long,
    private val onDedupeHit: (String) -> Unit,
) {
    constructor(
        ttlMs: Long = DEFAULT_TTL_MS,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        nowMs: () -> Long = System::currentTimeMillis,
        onDedupeHit: (String) -> Unit = {},
    ) : this(defaultBridgeCoreProvider(), ttlMs, maxEntries, nowMs, onDedupeHit)

    constructor(
        bridgeCore: BridgeCoreRuntime,
        ttlMs: Long = DEFAULT_TTL_MS,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        nowMs: () -> Long = System::currentTimeMillis,
        onDedupeHit: (String) -> Unit = {},
    ) : this({ bridgeCore }, ttlMs, maxEntries, nowMs, onDedupeHit)

    private data class Entry(
        val createdAtMs: Long,
        val response: CompletableFuture<ImageBridgeProtocol.ImageBridgeResponse>,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun validateAdmission(
        action: String,
        requestId: String?,
    ) = (bridgeCoreProvider() ?: error("bridge core unavailable for request admission"))
        .validateRequestAdmission(action, requestId)

    fun execute(
        key: String,
        block: () -> ImageBridgeProtocol.ImageBridgeResponse,
    ): ImageBridgeProtocol.ImageBridgeResponse {
        val core = bridgeCoreProvider() ?: error("bridge core unavailable for request dedupe")
        pruneExpired()
        val future = CompletableFuture<ImageBridgeProtocol.ImageBridgeResponse>()
        val existing = entries.putIfAbsent(key, Entry(createdAtMs = nowMs(), response = future))
        if (existing != null) {
            onDedupeHit(key)
            return existing.response.get(DEDUPE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }

        val admission = core.dedupeAdmit(key, nowMs()).dedupeState()
        if (admission is DedupeState.Cached) {
            onDedupeHit(key)
        }

        return try {
            val response = block()
            future.complete(response)
            core.dedupeComplete(key, COMPLETED_MARKER_JSON, nowMs())
            response
        } catch (error: Throwable) {
            val response =
                bridgeFailureResponse(
                    error = error.message ?: "internal error",
                    errorCode = ImageBridgeProtocol.ERROR_INTERNAL,
                )
            future.complete(response)
            core.dedupeComplete(key, COMPLETED_MARKER_JSON, nowMs())
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
        private const val COMPLETED_MARKER_JSON = "{}"
    }
}

private fun defaultBridgeCoreProvider(): () -> BridgeCoreRuntime? {
    val runtime by lazy {
        BridgeCore.loadOrNull(
            securityMode = BridgeSecurityMode.fromEnv().coreRawValue(),
            bridgeToken = party.qwer.iris.resolveBridgeToken(),
            requireHandshakeRaw = null,
        )
    }
    return { runtime }
}
