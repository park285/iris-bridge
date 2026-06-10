package party.qwer.iris.imagebridge.runtime.core

internal fun BridgeCore.looksLikeReplyAttachmentText(value: String): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching {
        BridgeCoreJniReply.nativeReplyAttachmentTextLooksLike(value)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core reply attachment text policy threw", error)
        false
    }
}

internal fun BridgeCore.replyAttachmentSessionId(attachmentText: String): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniReply.nativeReplyAttachmentSessionId(attachmentText)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core reply attachment session id policy threw", error)
        null
    }
}
