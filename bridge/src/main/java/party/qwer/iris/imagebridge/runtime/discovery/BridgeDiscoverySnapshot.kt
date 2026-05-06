package party.qwer.iris.imagebridge.runtime.discovery

internal data class DiscoveryHookStatus(
    val name: String,
    val installed: Boolean,
    val installError: String? = null,
    val invocationCount: Int,
    val lastSeenEpochMs: Long? = null,
    val lastSummary: String? = null,
)

internal data class BridgeDiscoverySnapshot(
    val installAttempted: Boolean,
    val hooks: List<DiscoveryHookStatus>,
)

internal fun BridgeDiscoverySnapshot.requiredSendHookName(imageCount: Int): String = if (imageCount == 1) HOOK_SEND_SINGLE else HOOK_SEND_MULTIPLE

internal fun BridgeDiscoverySnapshot.sendBlockReason(imageCount: Int): String? = sendBlockReason(imageCount, threadId = null, threadScope = null)

internal fun BridgeDiscoverySnapshot.sendBlockReason(
    imageCount: Int,
    threadId: Long?,
    threadScope: Int?,
): String? {
    if (!installAttempted) return "bridge discovery hooks not installed"
    val requiredHookNames =
        buildList {
            add(requiredSendHookName(imageCount))
            if (threadId != null && (threadScope ?: 0) >= 2) {
                add(HOOK_SEND_THREADED_ENTRY)
                add(HOOK_SEND_THREADED_INJECT)
            }
        }
    requiredHookNames.forEach { requiredHookName ->
        val hook =
            hooks.firstOrNull { it.name == requiredHookName }
                ?: return "bridge discovery hook missing from snapshot: $requiredHookName"
        if (!hook.installed) return "bridge discovery hook not ready: $requiredHookName"
    }
    return null
}
