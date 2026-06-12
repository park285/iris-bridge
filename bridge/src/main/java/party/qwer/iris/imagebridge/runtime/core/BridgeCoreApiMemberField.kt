package party.qwer.iris.imagebridge.runtime.core

private const val MEMBER_FIELD_MISSING_INT = -1

internal fun BridgeCore.memberFieldParseRoleCodeFromLong(value: Long): Int? = memberFieldParseRoleCodeFromLong(value, ::nativeMemberParseRoleCodeFromLong)

internal fun BridgeCore.memberFieldParseRoleCodeFromLong(
    value: Long,
    roleCodePolicy: (Long) -> Int?,
): Int? = memberFieldOptionalInt(roleCodePolicy(value), "bridge core unavailable to parse member role code")

internal fun BridgeCore.memberFieldParseRoleCodeFromString(value: String): Int? = memberFieldParseRoleCodeFromString(value, ::nativeMemberParseRoleCodeFromString)

internal fun BridgeCore.memberFieldParseRoleCodeFromString(
    value: String,
    roleCodePolicy: (String) -> Int?,
): Int? = memberFieldOptionalInt(roleCodePolicy(value), "bridge core unavailable to parse member role code")

internal fun BridgeCore.memberFieldLooksLikeNickname(value: String): Boolean = memberFieldLooksLikeNickname(value, ::nativeMemberLooksLikeNickname)

internal fun BridgeCore.memberFieldLooksLikeNickname(
    value: String,
    nicknamePolicy: (String) -> Boolean?,
): Boolean =
    nicknamePolicy(value)
        ?: error("bridge core unavailable to evaluate member nickname policy")

internal fun BridgeCore.memberFieldPrimitiveLongValueFromString(value: String): Long? {
    if (!bridgeCoreLoadLibraryOnce()) {
        error("bridge core unavailable to parse member primitive long")
    }
    return runCatching {
        BridgeCoreJniMemberField
            .nativeMemberPrimitiveLongValueFromString(value)
            ?.toLong()
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core member primitive long policy threw", error)
        error("bridge core unavailable to parse member primitive long")
    }
}

internal fun BridgeCore.memberFieldPathHintScore(
    path: String,
    preferredTokens: Set<String>,
    discouragedTokens: Set<String>,
): Int = memberFieldPathHintScore(path, preferredTokens, discouragedTokens, ::nativeMemberPathHintScore)

internal fun BridgeCore.memberFieldPathHintScore(
    path: String,
    preferredTokens: Set<String>,
    discouragedTokens: Set<String>,
    scorePolicy: (String, Set<String>, Set<String>) -> Int?,
): Int =
    scorePolicy(path, preferredTokens, discouragedTokens)
        ?: error("bridge core unavailable to score member field path")

internal fun BridgeCore.memberFieldLooksLikeMentionUserIdValue(
    value: String,
    userId: Long?,
    nickname: String?,
): Boolean =
    nativeMemberLooksLikeMentionUserIdValue(value, userId, nickname)
        ?: error("bridge core unavailable to evaluate member mention user id policy")

private fun memberFieldOptionalInt(
    value: Int?,
    unavailableMessage: String,
): Int? = (value ?: error(unavailableMessage)).takeIf { it != MEMBER_FIELD_MISSING_INT }

private fun nativeMemberParseRoleCodeFromLong(value: Long): Int? =
    memberFieldNative("bridge-core member role code long policy threw") {
        BridgeCoreJniMemberField.nativeMemberParseRoleCodeFromLong(value)
    }

private fun nativeMemberParseRoleCodeFromString(value: String): Int? =
    memberFieldNative("bridge-core member role code string policy threw") {
        BridgeCoreJniMemberField.nativeMemberParseRoleCodeFromString(value)
    }

private fun nativeMemberLooksLikeNickname(value: String): Boolean? =
    memberFieldNative("bridge-core member nickname policy threw") {
        BridgeCoreJniMemberField.nativeMemberLooksLikeNickname(value)
    }

private fun nativeMemberPathHintScore(
    path: String,
    preferredTokens: Set<String>,
    discouragedTokens: Set<String>,
): Int? =
    memberFieldNative("bridge-core member path hint policy threw") {
        BridgeCoreJniMemberField.nativeMemberPathHintScore(
            path,
            preferredTokens.toTypedArray(),
            discouragedTokens.toTypedArray(),
        )
    }

private fun nativeMemberLooksLikeMentionUserIdValue(
    value: String,
    userId: Long?,
    nickname: String?,
): Boolean? =
    memberFieldNative("bridge-core member mention user id policy threw") {
        BridgeCoreJniMemberField.nativeMemberLooksLikeMentionUserIdValue(
            value,
            userId ?: 0L,
            userId != null,
            nickname,
        )
    }

internal fun <T> memberFieldNative(
    errorMessage: String,
    nativeValue: () -> T,
): T? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching { nativeValue() }
        .getOrElse { error ->
            bridgeCoreLogError(errorMessage, error)
            null
        }
}
