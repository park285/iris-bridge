package party.qwer.iris.imagebridge.runtime.server

import java.util.concurrent.atomic.AtomicLong

internal class BridgeMuxMetrics {
    private val requestCancelled = AtomicLong()
    private val requestDeduplicated = AtomicLong()
    private val goawaySent = AtomicLong()
    private val writeCount = AtomicLong()
    private val writeLatencyNanosTotal = AtomicLong()
    private val writeLatencyNanosMax = AtomicLong()
    private val lateResponse = AtomicLong()

    fun recordRequestCancelled() = requestCancelled.incrementAndGet()

    fun recordRequestDeduplicated() = requestDeduplicated.incrementAndGet()

    fun recordGoawaySent() = goawaySent.incrementAndGet()

    fun recordWrite(durationNanos: Long) {
        val normalized = durationNanos.coerceAtLeast(0)
        writeCount.incrementAndGet()
        writeLatencyNanosTotal.addAndGet(normalized)
        updateMax(writeLatencyNanosMax, normalized)
    }

    fun recordLateResponse() = lateResponse.incrementAndGet()

    fun cancelCount(): Long = requestCancelled.get()

    fun deduplicatedCount(): Long = requestDeduplicated.get()

    fun goawayCount(): Long = goawaySent.get()

    fun sessionSnapshot(busyCount: Long): BridgeMuxSessionMetricsSnapshot =
        BridgeMuxSessionMetricsSnapshot(
            writeCount = writeCount.get(),
            writeLatencyNanosTotal = writeLatencyNanosTotal.get(),
            writeLatencyNanosMax = writeLatencyNanosMax.get(),
            busyCount = busyCount,
            cancelCount = requestCancelled.get(),
            lateResponseCount = lateResponse.get(),
        )

    private fun updateMax(
        target: AtomicLong,
        value: Long,
    ) {
        while (true) {
            val current = target.get()
            if (value <= current) return
            if (target.compareAndSet(current, value)) return
        }
    }
}
