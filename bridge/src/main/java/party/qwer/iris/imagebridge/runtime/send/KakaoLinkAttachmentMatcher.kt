package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.matchKakaoLinkAttachments
import party.qwer.iris.imagebridge.runtime.core.matchKakaoLinkPendingCleanupAttachments

internal fun kakaoLinkAttachmentsMatch(
    expectedRawAttachment: String,
    committedRawAttachment: String,
): Boolean = BridgeCore.matchKakaoLinkAttachments(expectedRawAttachment, committedRawAttachment)

internal fun kakaoLinkPendingCleanupAttachmentsMatch(
    expectedRawAttachment: String,
    pendingRawAttachment: String,
): Boolean = BridgeCore.matchKakaoLinkPendingCleanupAttachments(expectedRawAttachment, pendingRawAttachment)
