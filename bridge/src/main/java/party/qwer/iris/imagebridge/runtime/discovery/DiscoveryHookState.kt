package party.qwer.iris.imagebridge.runtime.discovery

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class DiscoveryHookState {
    private val installed = AtomicBoolean(false)
    private val installError = AtomicReference<String?>(null)
    private val invocationCount = AtomicInteger(0)
    private val lastSeenEpochMs = AtomicLong(0L)
    private val lastSummary = AtomicReference<String?>(null)

    fun markInstalled() {
        installed.set(true)
        installError.set(null)
    }

    fun markInstallError(detail: String) {
        installed.set(false)
        installError.set(detail)
    }

    fun record(summary: String) {
        invocationCount.incrementAndGet()
        lastSeenEpochMs.set(System.currentTimeMillis())
        lastSummary.set(summary)
    }

    fun toSnapshot(name: String): DiscoveryHookStatus =
        DiscoveryHookStatus(
            name = name,
            installed = installed.get(),
            installError = installError.get(),
            invocationCount = invocationCount.get(),
            lastSeenEpochMs = lastSeenEpochMs.get().takeIf { it > 0L },
            lastSummary = lastSummary.get(),
        )

    fun reset() {
        installed.set(false)
        installError.set(null)
        invocationCount.set(0)
        lastSeenEpochMs.set(0L)
        lastSummary.set(null)
    }
}
