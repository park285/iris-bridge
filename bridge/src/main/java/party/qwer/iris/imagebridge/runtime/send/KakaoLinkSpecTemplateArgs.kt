package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.extractKakaoLinkAppKey as coreExtractKakaoLinkAppKey
import party.qwer.iris.imagebridge.runtime.core.hasKakaoLinkExplicitTemplateArgs as coreHasKakaoLinkExplicitTemplateArgs
import party.qwer.iris.imagebridge.runtime.core.hasResolvedIrisKakaoLinkTemplate as coreHasResolvedIrisKakaoLinkTemplate

internal fun hasExplicitKakaoLinkTemplateArgs(rawAttachment: String): Boolean =
    BridgeCore.coreHasKakaoLinkExplicitTemplateArgs(rawAttachment)

internal fun hasResolvedIrisKakaoLinkTemplate(rawAttachment: String): Boolean =
    BridgeCore.coreHasResolvedIrisKakaoLinkTemplate(rawAttachment)

internal fun extractKakaoLinkAppKey(rawAttachment: String): String? = BridgeCore.coreExtractKakaoLinkAppKey(rawAttachment)
