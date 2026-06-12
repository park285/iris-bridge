package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.serverRestartDelayMs(failureCount: Int): Long = serverRestartDelayMs(failureCount, ::nativeServerRestartDelayMs)

internal fun BridgeCore.serverRestartDelayMs(
    failureCount: Int,
    restartDelayPolicy: (Int) -> Long?,
): Long =
    restartDelayPolicy(failureCount)
        ?: error("bridge core unavailable to resolve restart delay")

private fun nativeServerRestartDelayMs(failureCount: Int): Long? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniPolicy.nativeServerRestartDelayMs(failureCount) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core restart delay policy threw", error)
            null
        }
}
