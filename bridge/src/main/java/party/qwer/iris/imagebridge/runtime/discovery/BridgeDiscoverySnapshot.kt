package party.qwer.iris.imagebridge.runtime.discovery

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.sendBlockReasonRaw

private const val SEND_BLOCK_POLICY_UNAVAILABLE_REASON = "bridge core unavailable to evaluate bridge discovery hooks"
private typealias NativeSendBlockReason = (Boolean, Array<String>, BooleanArray, Int, Long?, Int?) -> String?

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

internal fun BridgeDiscoverySnapshot.sendBlockReason(imageCount: Int): String? =
    sendBlockReason(imageCount, threadId = null, threadScope = null)

internal fun BridgeDiscoverySnapshot.sendBlockReason(
    imageCount: Int,
    threadId: Long?,
    threadScope: Int?,
    nativeSendBlockReason: NativeSendBlockReason = BridgeCore::sendBlockReasonRaw,
): String? {
    val nativeReason =
        nativeSendBlockReason(
            installAttempted,
            hookNames(),
            hookInstalled(),
            imageCount,
            threadId,
            threadScope,
        )
    if (nativeReason != null) return nativeReason.ifEmpty { null }
    return SEND_BLOCK_POLICY_UNAVAILABLE_REASON
}

private fun BridgeDiscoverySnapshot.hookNames(): Array<String> =
    hooks.map { hook -> hook.name }.toTypedArray()

private fun BridgeDiscoverySnapshot.hookInstalled(): BooleanArray =
    BooleanArray(hooks.size) { index -> hooks[index].installed }
