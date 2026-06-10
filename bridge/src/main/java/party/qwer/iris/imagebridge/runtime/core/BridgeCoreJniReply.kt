package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniReply {
    external fun nativeReplyHookSign(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String,
        createdAtEpochMs: Long,
        mentionsHash: String?,
    ): String?

    external fun nativeReplyHookVerify(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String?,
        createdAtEpochMs: Long,
        mentionsHash: String?,
        signature: String?,
        nowEpochMs: Long,
    ): Boolean

    external fun nativeMentionsHashFromJson(mentionsJson: String?): String?

    external fun nativeMentionsHashFromAttachment(attachmentText: String?): String?

    external fun nativeReplyMentionAttachmentOrNull(attachmentText: String): String?

    external fun nativeMergeReplyMentionAttachment(
        targetAttachmentText: String,
        mentionAttachmentText: String,
    ): String?

    external fun nativeMergeReplyLeverageAttachment(
        generatedAttachment: String?,
        rawAttachment: String,
    ): String?

    external fun nativeReplyAttachmentTextLooksLike(value: String): Boolean

    external fun nativeReplyAttachmentSessionId(attachmentText: String): String?
}
