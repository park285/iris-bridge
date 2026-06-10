package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniKakaoLink {
    external fun nativeKakaoLinkAttachmentsMatch(
        expectedRawAttachment: String,
        committedRawAttachment: String,
    ): Boolean

    external fun nativeKakaoLinkPendingCleanupAttachmentsMatch(
        expectedRawAttachment: String,
        pendingRawAttachment: String,
    ): Boolean

    external fun nativeKakaoLinkLeverageEncryptionType(value: String): Int

    external fun nativeKakaoLinkHasExplicitTemplateArgs(rawAttachment: String): Boolean

    external fun nativeKakaoLinkHasResolvedIrisTemplate(rawAttachment: String): Boolean

    external fun nativeKakaoLinkExtractAppKey(rawAttachment: String): String?

    external fun nativeBuildKakaoLinkV4EncodedQuery(rawAttachment: String): String?

    external fun nativeBuildKakaoLinkSpecSendAttachment(rawAttachment: String): String?

    external fun nativePatchKakaoLinkDisplayAttachment(
        committedAttachment: String?,
        rawAttachment: String,
    ): String?
}
