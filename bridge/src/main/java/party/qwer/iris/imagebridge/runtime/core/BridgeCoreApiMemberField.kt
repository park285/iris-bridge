package party.qwer.iris.imagebridge.runtime.core

private const val MEMBER_FIELD_MISSING_INT = -1

internal fun BridgeCore.memberFieldParseRoleCodeFromLong(value: Long): Int? =
    memberFieldOptionalInt("bridge-core member role code long policy threw") {
        BridgeCoreJniMemberField.nativeMemberParseRoleCodeFromLong(value)
    }

internal fun BridgeCore.memberFieldParseRoleCodeFromString(value: String): Int? =
    memberFieldOptionalInt("bridge-core member role code string policy threw") {
        BridgeCoreJniMemberField.nativeMemberParseRoleCodeFromString(value)
    }

internal fun BridgeCore.memberFieldLooksLikeNickname(value: String): Boolean =
    memberFieldBoolean("bridge-core member nickname policy threw") {
        BridgeCoreJniMemberField.nativeMemberLooksLikeNickname(value)
    }

internal fun BridgeCore.memberFieldLooksLikeProfileUrl(value: String): Boolean =
    memberFieldBoolean("bridge-core member profile URL policy threw") {
        BridgeCoreJniMemberField.nativeMemberLooksLikeProfileUrl(value)
    }

internal fun BridgeCore.memberFieldPrimitiveLongValueFromString(value: String): Long? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        BridgeCoreJniMemberField
            .nativeMemberPrimitiveLongValueFromString(value)
            ?.toLong()
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core member primitive long policy threw", error)
        null
    }
}

internal fun BridgeCore.memberFieldPathHintScore(
    path: String,
    preferredTokens: Set<String>,
    discouragedTokens: Set<String>,
): Int =
    memberFieldInt("bridge-core member path hint policy threw", defaultValue = 0) {
        BridgeCoreJniMemberField.nativeMemberPathHintScore(
            path,
            preferredTokens.toTypedArray(),
            discouragedTokens.toTypedArray(),
        )
    }

internal fun BridgeCore.memberFieldLooksLikeMentionUserIdValue(
    value: String,
    userId: Long?,
    nickname: String?,
): Boolean =
    memberFieldBoolean("bridge-core member mention user id policy threw") {
        BridgeCoreJniMemberField.nativeMemberLooksLikeMentionUserIdValue(
            value,
            userId ?: 0L,
            userId != null,
            nickname,
        )
    }

internal fun BridgeCore.memberFieldNicknameQualityScore(value: String): Int =
    memberFieldInt("bridge-core member nickname quality policy threw", defaultValue = 0) {
        BridgeCoreJniMemberField.nativeMemberNicknameQualityScore(value)
    }

internal fun BridgeCore.memberFieldNicknameIsTrustedForDisplay(
    userId: Long,
    nickname: String,
): Boolean =
    memberFieldBoolean("bridge-core member trusted nickname policy threw") {
        BridgeCoreJniMemberField.nativeMemberNicknameIsTrustedForDisplay(userId, nickname)
    }

internal fun BridgeCore.memberFieldGenericLabelPenalty(value: String): Int =
    memberFieldInt("bridge-core member generic label policy threw", defaultValue = 0) {
        BridgeCoreJniMemberField.nativeMemberGenericLabelPenalty(value)
    }

private fun BridgeCore.memberFieldOptionalInt(
    errorMessage: String,
    nativeValue: () -> Int,
): Int? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        nativeValue().takeIf { value -> value != MEMBER_FIELD_MISSING_INT }
    }.getOrElse { error ->
        bridgeCoreLogError(errorMessage, error)
        null
    }
}

private fun BridgeCore.memberFieldBoolean(
    errorMessage: String,
    nativeValue: () -> Boolean,
): Boolean {
    if (!bridgeCoreLoadLibraryOnce()) return false
    return runCatching { nativeValue() }
        .getOrElse { error ->
            bridgeCoreLogError(errorMessage, error)
            false
        }
}

private fun BridgeCore.memberFieldInt(
    errorMessage: String,
    defaultValue: Int,
    nativeValue: () -> Int,
): Int {
    if (!bridgeCoreLoadLibraryOnce()) return defaultValue
    return runCatching { nativeValue() }
        .getOrElse { error ->
            bridgeCoreLogError(errorMessage, error)
            defaultValue
        }
}
