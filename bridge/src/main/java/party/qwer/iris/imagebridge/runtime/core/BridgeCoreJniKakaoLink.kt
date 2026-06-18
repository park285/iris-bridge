package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniKakaoLink {
    internal fun encryptKakaoChatLogAttachmentEnvelope(
        encType: Int,
        plaintext: String,
        userId: Long,
    ): String =
        nativeKakaoChatLogAttachmentCrypto(
            encrypt = true,
            encType = encType,
            payload = plaintext,
            userId = userId,
        )

    internal fun decryptKakaoChatLogAttachmentEnvelope(
        encType: Int,
        ciphertext: String,
        userId: Long,
    ): String =
        nativeKakaoChatLogAttachmentCrypto(
            encrypt = false,
            encType = encType,
            payload = ciphertext,
            userId = userId,
        )

    private fun nativeKakaoChatLogAttachmentCrypto(
        encrypt: Boolean,
        encType: Int,
        payload: String,
        userId: Long,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "kakaoLink.chatLogAttachmentCrypto",
            JSONObject()
                .put("encrypt", encrypt)
                .put("encType", encType)
                .put("payload", payload)
                .put("userId", userId),
        )

    fun nativeKakaoLinkAttachmentsMatch(
        expectedRawAttachment: String,
        committedRawAttachment: String,
    ): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "kakaoLink.attachmentsMatch",
            JSONObject()
                .put("expectedRawAttachment", expectedRawAttachment)
                .put("committedRawAttachment", committedRawAttachment),
        )

    fun nativeKakaoLinkPendingCleanupAttachmentsMatch(
        expectedRawAttachment: String,
        pendingRawAttachment: String,
    ): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "kakaoLink.pendingCleanupAttachmentsMatch",
            JSONObject()
                .put("expectedRawAttachment", expectedRawAttachment)
                .put("pendingRawAttachment", pendingRawAttachment),
        )

    fun nativeKakaoLinkLeverageEncryptionType(value: String): Int =
        BridgeCoreJniDispatcher.intValue(
            "kakaoLink.leverageEncryptionType",
            JSONObject().put("value", value),
        )

    fun nativeKakaoLinkHasExplicitTemplateArgs(rawAttachment: String): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "kakaoLink.hasExplicitTemplateArgs",
            JSONObject().put("rawAttachment", rawAttachment),
        )

    fun nativeKakaoLinkHasResolvedIrisTemplate(rawAttachment: String): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "kakaoLink.hasResolvedIrisTemplate",
            JSONObject().put("rawAttachment", rawAttachment),
        )

    fun nativeKakaoLinkExtractAppKey(rawAttachment: String): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "kakaoLink.extractAppKey",
            JSONObject().put("rawAttachment", rawAttachment),
        )

    fun nativeBuildKakaoLinkV4EncodedQuery(rawAttachment: String): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "kakaoLink.buildV4EncodedQuery",
            JSONObject().put("rawAttachment", rawAttachment),
        )

    fun nativeBuildKakaoLinkSpecSendAttachment(rawAttachment: String): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "kakaoLink.buildSpecSendAttachment",
            JSONObject().put("rawAttachment", rawAttachment),
        )

    fun nativePatchKakaoLinkDisplayAttachment(
        committedAttachment: String?,
        rawAttachment: String,
    ): String? =
        BridgeCoreJniDispatcher.optionalStringValue(
            "kakaoLink.patchDisplayAttachment",
            JSONObject()
                .putNullable("committedAttachment", committedAttachment)
                .put("rawAttachment", rawAttachment),
        )
}
