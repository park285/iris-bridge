package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniRequest {
    fun nativeValidateRequestToken(
        handle: Long,
        requestJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "request.validateToken",
            JSONObject()
                .put("handle", handle)
                .put("requestJson", requestJson),
        )

    fun nativeValidateRequestAdmission(
        handle: Long,
        action: String,
        requestId: String?,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "request.validateAdmission",
            JSONObject()
                .put("handle", handle)
                .put("action", action)
                .putNullable("requestId", requestId),
        )

    fun nativeValidateTextRequest(
        handle: Long,
        hasRoomId: Boolean,
        roomId: Long,
        message: String?,
        markdown: Boolean,
        attachmentJson: String?,
        mentionsJson: String?,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "request.validateText",
            JSONObject()
                .put("handle", handle)
                .apply {
                    if (hasRoomId) put("roomId", roomId) else put("roomId", JSONObject.NULL)
                }.putNullable("message", message)
                .put("markdown", markdown)
                .putNullable("attachmentJson", attachmentJson)
                .putNullable("mentionsJson", mentionsJson),
        )

    fun nativeValidateImagePaths(
        handle: Long,
        imagePathsJson: String,
        maxPathCount: Int,
        maxPathLength: Int,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "request.validateImagePaths",
            JSONObject()
                .put("handle", handle)
                .put("imagePathsJson", imagePathsJson)
                .put("maxPathCount", maxPathCount)
                .put("maxPathLength", maxPathLength),
        )

    fun nativeClassifyErrorCode(
        message: String,
        isIllegalArgument: Boolean,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "request.classifyErrorCode",
            JSONObject()
                .put("message", message)
                .put("isIllegalArgument", isIllegalArgument),
        )

    fun nativeFailureMetricBucket(errorCode: String): String =
        BridgeCoreJniDispatcher.stringValue(
            "request.failureMetricBucket",
            JSONObject().put("errorCode", errorCode),
        )

    fun nativeRequestRequiresRequestId(action: String): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "request.requiresRequestId",
            JSONObject().put("action", action),
        )

    fun nativeRequestDedupeKey(
        action: String,
        requestId: String?,
    ): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "request.dedupeKey",
            JSONObject()
                .put("action", action)
                .putNullable("requestId", requestId),
        )
}
