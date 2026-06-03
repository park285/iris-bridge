package party.qwer.iris.imagebridge.runtime.server

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal const val DEFAULT_MAX_CONCURRENT_SESSIONS = 16

internal interface BridgeSessionAdmission {
    fun tryExecute(work: Runnable): Boolean

    fun activeSessionCount(): Int
}

internal class BoundedBridgeSessionAdmission(
    private val executor: ExecutorService,
    private val maxConcurrentSessions: Int = DEFAULT_MAX_CONCURRENT_SESSIONS,
    private val onActiveSessionCount: (Long) -> Unit = {},
) : BridgeSessionAdmission {
    private val active = AtomicInteger(0)

    override fun tryExecute(work: Runnable): Boolean {
        if (active.incrementAndGet() > maxConcurrentSessions) {
            release()
            return false
        }
        return try {
            executor.execute {
                try {
                    work.run()
                } finally {
                    release()
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            release()
            false
        }
    }

    override fun activeSessionCount(): Int = active.get().coerceAtLeast(0)

    private fun release() {
        onActiveSessionCount(active.decrementAndGet().toLong().coerceAtLeast(0))
    }
}

internal fun newBridgeSessionAdmission(
    metrics: BridgeMetrics,
    executorProvider: () -> ExecutorService = ::newBridgeSessionExecutor,
): BridgeSessionAdmission =
    BoundedBridgeSessionAdmission(
        executor = executorProvider(),
        onActiveSessionCount = metrics::recordQueuedClientCount,
    )

internal fun newBridgeSessionExecutor(maxConcurrentSessions: Int = DEFAULT_MAX_CONCURRENT_SESSIONS): ExecutorService =
    ThreadPoolExecutor(
        maxConcurrentSessions,
        maxConcurrentSessions,
        SESSION_EXECUTOR_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { runnable -> Thread(runnable, "iris-bridge-mux-client").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply {
        allowCoreThreadTimeOut(true)
    }

private const val SESSION_EXECUTOR_KEEP_ALIVE_MS = 60_000L
