package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniReply {
    fun nativeReplyHookSign(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String,
        createdAtEpochMs: Long,
        mentionsHash: String?,
    ): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.sign",
            JSONObject()
                .put("bridgeToken", bridgeToken)
                .put("roomId", roomId)
                .put("messageText", messageText)
                .put("sessionId", sessionId)
                .put("createdAtEpochMs", createdAtEpochMs)
                .putNullable("mentionsHash", mentionsHash),
        )

    fun nativeReplyHookVerify(
        bridgeToken: String,
        roomId: Long,
        messageText: String,
        sessionId: String?,
        createdAtEpochMs: Long,
        mentionsHash: String?,
        signature: String?,
        nowEpochMs: Long,
    ): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "reply.verify",
            JSONObject()
                .put("bridgeToken", bridgeToken)
                .put("roomId", roomId)
                .put("messageText", messageText)
                .putNullable("sessionId", sessionId)
                .put("createdAtEpochMs", createdAtEpochMs)
                .putNullable("mentionsHash", mentionsHash)
                .putNullable("signature", signature)
                .put("nowEpochMs", nowEpochMs),
        )

    fun nativeMentionsHashFromJson(mentionsJson: String?): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.mentionsHashFromJson",
            JSONObject().putNullable("mentionsJson", mentionsJson),
        )

    fun nativeMentionsHashFromAttachment(attachmentText: String?): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.mentionsHashFromAttachment",
            JSONObject().putNullable("attachmentText", attachmentText),
        )

    fun nativeReplyMentionAttachmentOrNull(attachmentText: String): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.mentionAttachmentOrNull",
            JSONObject().put("attachmentText", attachmentText),
        )

    fun nativeMergeReplyMentionAttachment(
        targetAttachmentText: String,
        mentionAttachmentText: String,
    ): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.mergeMentionAttachment",
            JSONObject()
                .put("targetAttachmentText", targetAttachmentText)
                .put("mentionAttachmentText", mentionAttachmentText),
        )

    fun nativeMergeReplyLeverageAttachment(
        generatedAttachment: String?,
        rawAttachment: String,
    ): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.mergeLeverageAttachment",
            JSONObject()
                .putNullable("generatedAttachment", generatedAttachment)
                .put("rawAttachment", rawAttachment),
        )

    fun nativeReplyAttachmentTextLooksLike(value: String): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "reply.attachmentTextLooksLike",
            JSONObject().put("value", value),
        )

    fun nativeReplyAttachmentSessionId(attachmentText: String): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "reply.attachmentSessionId",
            JSONObject().put("attachmentText", attachmentText),
        )

    fun nativeReplyMarkdownPendingContext(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "reply.markdownPendingContext",
            JSONObject().put("requestJson", requestJson),
        )

    fun nativeReplyMentionPendingContext(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "reply.mentionPendingContext",
            JSONObject().put("requestJson", requestJson),
        )
}
