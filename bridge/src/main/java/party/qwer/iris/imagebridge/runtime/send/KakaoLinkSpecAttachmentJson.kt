package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.buildKakaoLinkSpecSendAttachment as coreBuildKakaoLinkSpecSendAttachment
import party.qwer.iris.imagebridge.runtime.core.patchKakaoLinkDisplayAttachment as corePatchKakaoLinkDisplayAttachment

internal fun kakaoLinkSpecSendAttachment(rawAttachment: String): String = kakaoLinkSpecSendAttachment(rawAttachment, BridgeCore::coreBuildKakaoLinkSpecSendAttachment)

internal fun kakaoLinkSpecSendAttachment(
    rawAttachment: String,
    buildAttachment: (String) -> String?,
): String =
    buildAttachment(rawAttachment)
        ?: error("bridge core unavailable to build KakaoLinkSpec send attachment")

internal fun kakaoLinkSpecCommitVerificationAttachment(rawAttachment: String): String = rawAttachment

internal fun kakaoLinkSpecPatchMatchAttachments(rawAttachment: String): List<String> = listOf(rawAttachment)

internal fun kakaoLinkDisplayPatchAttachment(
    committedAttachment: String?,
    rawAttachment: String,
): String =
    kakaoLinkDisplayPatchAttachment(
        committedAttachment,
        rawAttachment,
        BridgeCore::corePatchKakaoLinkDisplayAttachment,
    )

internal fun kakaoLinkDisplayPatchAttachment(
    committedAttachment: String?,
    rawAttachment: String,
    patchAttachment: (String?, String) -> String?,
): String =
    patchAttachment(committedAttachment, rawAttachment)
        ?: error("bridge core unavailable to patch KakaoLink display attachment")
