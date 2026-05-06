package party.qwer.iris.imagebridge.runtime.server

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private const val CLIENT_EXECUTOR_CORE_THREADS = 2
private const val CLIENT_EXECUTOR_MAX_THREADS = 8
private const val CLIENT_EXECUTOR_QUEUE_CAPACITY = 64
private const val CLIENT_EXECUTOR_KEEP_ALIVE_MS = 60_000L

internal fun newBridgeClientExecutor(): ThreadPoolExecutor =
    ThreadPoolExecutor(
        CLIENT_EXECUTOR_CORE_THREADS,
        CLIENT_EXECUTOR_MAX_THREADS,
        CLIENT_EXECUTOR_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(CLIENT_EXECUTOR_QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "iris-bridge-client").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply {
        allowCoreThreadTimeOut(true)
    }
