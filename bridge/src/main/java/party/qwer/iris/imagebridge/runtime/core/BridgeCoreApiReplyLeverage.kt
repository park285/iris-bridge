package party.qwer.iris.imagebridge.runtime.core

internal fun BridgeCore.mergeReplyLeverageAttachment(
    generatedAttachment: String?,
    rawAttachment: String,
): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniReply.nativeMergeReplyLeverageAttachment(
            generatedAttachment,
            rawAttachment,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core reply leverage attachment merge threw", error)
        null
    }
}
