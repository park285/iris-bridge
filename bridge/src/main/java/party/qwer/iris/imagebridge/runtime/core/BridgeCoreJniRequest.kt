package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniRequest {
    external fun nativeValidateRequestToken(
        handle: Long,
        requestJson: String,
    ): String

    external fun nativeValidateRequestAdmission(
        handle: Long,
        action: String,
        requestId: String?,
    ): String

    external fun nativeValidateTextRequest(
        handle: Long,
        hasRoomId: Boolean,
        roomId: Long,
        message: String?,
        markdown: Boolean,
        attachmentJson: String?,
        mentionsJson: String?,
    ): String

    external fun nativeValidateImagePaths(
        handle: Long,
        imagePathsJson: String,
        maxPathCount: Int,
        maxPathLength: Int,
    ): String

    external fun nativeClassifyErrorCode(
        message: String,
        isIllegalArgument: Boolean,
    ): String

    external fun nativeRequestRequiresRequestId(action: String): Boolean

    external fun nativeRequestDedupeKey(
        action: String,
        requestId: String?,
    ): String?
}
