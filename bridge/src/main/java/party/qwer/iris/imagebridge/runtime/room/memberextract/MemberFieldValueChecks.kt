package party.qwer.iris.imagebridge.runtime.room.memberextract

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.memberFieldGenericLabelPenalty
import party.qwer.iris.imagebridge.runtime.core.memberFieldLooksLikeMentionUserIdValue
import party.qwer.iris.imagebridge.runtime.core.memberFieldLooksLikeNickname
import party.qwer.iris.imagebridge.runtime.core.memberFieldLooksLikeProfileUrl
import party.qwer.iris.imagebridge.runtime.core.memberFieldNicknameQualityScore
import party.qwer.iris.imagebridge.runtime.core.memberFieldParseRoleCodeFromLong
import party.qwer.iris.imagebridge.runtime.core.memberFieldParseRoleCodeFromString
import party.qwer.iris.imagebridge.runtime.core.memberFieldPathHintScore
import party.qwer.iris.imagebridge.runtime.core.memberFieldPrimitiveLongValueFromString

internal fun parseRoleCode(value: PrimitiveValue?): Int? =
    when (value) {
        is PrimitiveValue.LongValue -> BridgeCore.memberFieldParseRoleCodeFromLong(value.value)
        is PrimitiveValue.StringValue -> BridgeCore.memberFieldParseRoleCodeFromString(value.value)
        null -> null
    }

internal fun looksLikeNickname(value: String): Boolean = BridgeCore.memberFieldLooksLikeNickname(value)

internal fun looksLikeProfileUrl(value: String): Boolean = BridgeCore.memberFieldLooksLikeProfileUrl(value)

internal fun primitiveLongValue(value: PrimitiveValue?): Long? =
    when (value) {
        is PrimitiveValue.LongValue -> value.value
        is PrimitiveValue.StringValue -> BridgeCore.memberFieldPrimitiveLongValueFromString(value.value)
        null -> null
    }

internal fun pathHintScore(
    path: String,
    preferredTokens: Set<String>,
    discouragedTokens: Set<String>,
): Int = BridgeCore.memberFieldPathHintScore(path, preferredTokens, discouragedTokens)

internal fun looksLikeMentionUserIdValue(
    value: String,
    userId: Long?,
    nickname: String?,
): Boolean = BridgeCore.memberFieldLooksLikeMentionUserIdValue(value, userId, nickname)

internal fun nicknameQualityScore(value: String): Int = BridgeCore.memberFieldNicknameQualityScore(value)

internal fun genericLabelPenalty(value: String): Int = BridgeCore.memberFieldGenericLabelPenalty(value)
