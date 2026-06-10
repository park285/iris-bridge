package party.qwer.iris.imagebridge.runtime.core

object BridgeCore {
    const val EXPECTED_ABI_VERSION: Int = 1

    external fun nativeCreateContext(
        mode: String?,
        token: String,
        requireHandshakeRaw: String?,
    ): Long

    external fun nativeDestroyContext(handle: Long)

    external fun nativeHandshakeOnHello(
        handle: Long,
        frameJson: String,
        nowMs: Long,
        socketName: String,
    ): String

    external fun nativeHandshakeOnClientProof(
        handle: Long,
        frameJson: String,
    ): String

    external fun nativeValidateRequestToken(
        handle: Long,
        requestJson: String,
    ): String

    external fun nativeVerifyLeases(
        handle: Long,
        roomId: Long,
        requestId: String,
        leasesJson: String,
        factsJson: String,
        nowMs: Long,
    ): String

    external fun nativeDedupeAdmit(
        handle: Long,
        key: String,
        nowMs: Long,
    ): String

    external fun nativeDedupeComplete(
        handle: Long,
        key: String,
        responseJson: String,
        nowMs: Long,
    )

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

    external fun nativeRequireHandshake(handle: Long): Boolean

    external fun nativeAbiVersion(): Int
}
