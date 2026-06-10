package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.matchKakaoLinkAttachments(
    expectedRawAttachment: String,
    committedRawAttachment: String,
): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkAttachmentsMatch(expectedRawAttachment, committedRawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink attachment match threw", error)
        false
    }
}

fun BridgeCore.matchKakaoLinkPendingCleanupAttachments(
    expectedRawAttachment: String,
    pendingRawAttachment: String,
): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkPendingCleanupAttachmentsMatch(
            expectedRawAttachment,
            pendingRawAttachment,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink pending attachment match threw", error)
        false
    }
}

fun BridgeCore.kakaoLinkLeverageEncryptionType(value: String): Int {
    if (!bridgeCoreLoadLibraryOnce()) return 31
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkLeverageEncryptionType(value)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink leverage encryption type policy threw", error)
        31
    }
}

fun BridgeCore.hasKakaoLinkExplicitTemplateArgs(rawAttachment: String): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkHasExplicitTemplateArgs(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink explicit template args check threw", error)
        false
    }
}

fun BridgeCore.hasResolvedIrisKakaoLinkTemplate(rawAttachment: String): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkHasResolvedIrisTemplate(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink resolved template check threw", error)
        false
    }
}

fun BridgeCore.extractKakaoLinkAppKey(rawAttachment: String): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkExtractAppKey(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink app key extraction threw", error)
        null
    }
}

fun BridgeCore.buildKakaoLinkV4EncodedQuery(rawAttachment: String): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeBuildKakaoLinkV4EncodedQuery(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink V4 query build threw", error)
        null
    }
}

fun BridgeCore.buildKakaoLinkSpecSendAttachment(rawAttachment: String): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeBuildKakaoLinkSpecSendAttachment(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink spec send attachment build threw", error)
        null
    }
}

fun BridgeCore.patchKakaoLinkDisplayAttachment(
    committedAttachment: String?,
    rawAttachment: String,
): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativePatchKakaoLinkDisplayAttachment(
            committedAttachment,
            rawAttachment,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink display patch threw", error)
        null
    }
}
