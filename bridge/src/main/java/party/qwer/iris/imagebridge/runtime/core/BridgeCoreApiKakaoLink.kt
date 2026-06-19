package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.matchKakaoLinkAttachments(
    expectedRawAttachment: String,
    committedRawAttachment: String,
): Boolean =
    matchKakaoLinkAttachments(
        expectedRawAttachment,
        committedRawAttachment,
        ::nativeKakaoLinkAttachmentsMatch,
    )

internal fun BridgeCore.matchKakaoLinkAttachments(
    expectedRawAttachment: String,
    committedRawAttachment: String,
    matchPolicy: (String, String) -> Boolean?,
): Boolean =
    matchPolicy(expectedRawAttachment, committedRawAttachment)
        ?: error("bridge core unavailable to match KakaoLink attachments")

private fun nativeKakaoLinkAttachmentsMatch(
    expectedRawAttachment: String,
    committedRawAttachment: String,
): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkAttachmentsMatch(expectedRawAttachment, committedRawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink attachment match threw", error)
        null
    }
}

fun BridgeCore.matchKakaoLinkPendingCleanupAttachments(
    expectedRawAttachment: String,
    pendingRawAttachment: String,
): Boolean =
    nativeKakaoLinkPendingCleanupAttachmentsMatch(expectedRawAttachment, pendingRawAttachment)
        ?: error("bridge core unavailable to match pending KakaoLink attachments")

private fun nativeKakaoLinkPendingCleanupAttachmentsMatch(
    expectedRawAttachment: String,
    pendingRawAttachment: String,
): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkPendingCleanupAttachmentsMatch(
            expectedRawAttachment,
            pendingRawAttachment,
        )
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink pending attachment match threw", error)
        null
    }
}

fun BridgeCore.kakaoLinkLeverageEncryptionType(value: String): Int =
    kakaoLinkLeverageEncryptionType(value, ::nativeKakaoLinkLeverageEncryptionType)

internal fun BridgeCore.kakaoLinkLeverageEncryptionType(
    value: String,
    encryptionTypePolicy: (String) -> Int?,
): Int =
    encryptionTypePolicy(value)
        ?: error("bridge core unavailable to resolve KakaoLink leverage encryption type")

private fun nativeKakaoLinkLeverageEncryptionType(value: String): Int? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkLeverageEncryptionType(value)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink leverage encryption type policy threw", error)
        null
    }
}

fun BridgeCore.hasKakaoLinkExplicitTemplateArgs(rawAttachment: String): Boolean =
    hasKakaoLinkExplicitTemplateArgs(rawAttachment, ::nativeKakaoLinkHasExplicitTemplateArgs)

internal fun BridgeCore.hasKakaoLinkExplicitTemplateArgs(
    rawAttachment: String,
    templateArgsPolicy: (String) -> Boolean?,
): Boolean =
    templateArgsPolicy(rawAttachment)
        ?: error("bridge core unavailable to evaluate KakaoLink explicit template args")

private fun nativeKakaoLinkHasExplicitTemplateArgs(rawAttachment: String): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkHasExplicitTemplateArgs(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink explicit template args check threw", error)
        null
    }
}

fun BridgeCore.hasResolvedIrisKakaoLinkTemplate(rawAttachment: String): Boolean =
    nativeKakaoLinkHasResolvedIrisTemplate(rawAttachment)
        ?: error("bridge core unavailable to evaluate resolved Iris KakaoLink template")

private fun nativeKakaoLinkHasResolvedIrisTemplate(rawAttachment: String): Boolean? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniKakaoLink.nativeKakaoLinkHasResolvedIrisTemplate(rawAttachment)
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core kakaolink resolved template check threw", error)
        null
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
