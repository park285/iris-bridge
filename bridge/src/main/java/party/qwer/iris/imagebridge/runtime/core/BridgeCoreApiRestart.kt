package party.qwer.iris.imagebridge.runtime.core

private const val RESTART_POLICY_UNAVAILABLE_DELAY_MS = 30_000L

fun BridgeCore.serverRestartDelayMs(failureCount: Int): Long = serverRestartDelayMs(failureCount, ::nativeServerRestartDelayMs)

internal fun BridgeCore.serverRestartDelayMs(
    failureCount: Int,
    restartDelayPolicy: (Int) -> Long?,
): Long = restartDelayPolicy(failureCount) ?: RESTART_POLICY_UNAVAILABLE_DELAY_MS

private fun nativeServerRestartDelayMs(failureCount: Int): Long? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniPolicy.nativeServerRestartDelayMs(failureCount) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core restart delay policy threw", error)
            null
        }
}
