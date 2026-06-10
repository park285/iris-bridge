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
}
