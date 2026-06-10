package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniMemberField {
    external fun nativeMemberParseRoleCodeFromLong(value: Long): Int

    external fun nativeMemberParseRoleCodeFromString(value: String): Int

    external fun nativeMemberLooksLikeNickname(value: String): Boolean

    external fun nativeMemberLooksLikeProfileUrl(value: String): Boolean

    external fun nativeMemberPrimitiveLongValueFromString(value: String): String?

    external fun nativeMemberPathHintScore(
        path: String,
        preferredTokens: Array<String>,
        discouragedTokens: Array<String>,
    ): Int

    external fun nativeMemberLooksLikeMentionUserIdValue(
        value: String,
        userId: Long,
        hasUserId: Boolean,
        nickname: String?,
    ): Boolean

    external fun nativeMemberNicknameQualityScore(value: String): Int

    external fun nativeMemberGenericLabelPenalty(value: String): Int
}
