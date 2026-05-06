package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal fun logBridgeSpecFailure(
    tag: String,
    initialSpecStatus: BridgeSpecStatus,
) {
    if (!initialSpecStatus.ready) {
        Log.e(tag, "bridge hook spec verification failed: ${initialSpecStatus.checks.filterNot { it.ok }.joinToString { it.name }}")
    }
}

internal fun recordBridgeServerFailure(
    restartCount: AtomicInteger,
    lastCrashMessage: AtomicReference<String?>,
    message: String,
) {
    restartCount.incrementAndGet()
    lastCrashMessage.set(message)
}

internal fun sleepBeforeBridgeRestart(
    delayMs: Long,
    running: AtomicBoolean,
) {
    runCatching {
        Thread.sleep(delayMs)
    }.onFailure { error ->
        if (error is InterruptedException) {
            Thread.currentThread().interrupt()
            running.set(false)
        }
    }
}
