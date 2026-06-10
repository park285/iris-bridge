package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.buildKakaoLinkSpecSendAttachment as coreBuildKakaoLinkSpecSendAttachment
import party.qwer.iris.imagebridge.runtime.core.patchKakaoLinkDisplayAttachment as corePatchKakaoLinkDisplayAttachment

internal fun kakaoLinkSpecSendAttachment(rawAttachment: String): String = BridgeCore.coreBuildKakaoLinkSpecSendAttachment(rawAttachment) ?: rawAttachment

internal fun kakaoLinkSpecCommitVerificationAttachment(rawAttachment: String): String = rawAttachment

internal fun kakaoLinkSpecPatchMatchAttachments(rawAttachment: String): List<String> = listOf(rawAttachment)

internal fun kakaoLinkDisplayPatchAttachment(
    committedAttachment: String?,
    rawAttachment: String,
): String = BridgeCore.corePatchKakaoLinkDisplayAttachment(committedAttachment, rawAttachment) ?: rawAttachment
