package party.qwer.iris.imagebridge.runtime.core

internal fun BridgeCore.replyMentionAttachmentOrNull(attachmentText: String): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { BridgeCoreJniReply.nativeReplyMentionAttachmentOrNull(attachmentText) }
        .getOrElse { error ->
            bridgeCoreLogError("bridge-core reply mention attachment policy threw", error)
            null
        }
}

internal fun BridgeCore.mergeReplyMentionAttachment(
    targetAttachmentText: String,
    mentionAttachmentText: String,
): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniReply.nativeMergeReplyMentionAttachment(
            targetAttachmentText,
            mentionAttachmentText,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core reply mention attachment merge threw", error)
        null
    }
}
