package party.qwer.iris.imagebridge.runtime.core

internal fun BridgeCore.memberFieldLooksLikeProfileUrl(value: String): Boolean =
    nativeMemberLooksLikeProfileUrl(value)
        ?: error("bridge core unavailable to evaluate member profile URL policy")

internal fun BridgeCore.memberFieldNicknameQualityScore(value: String): Int =
    nativeMemberNicknameQualityScore(value)
        ?: error("bridge core unavailable to score member nickname quality")

internal fun BridgeCore.memberFieldNicknameIsTrustedForDisplay(
    userId: Long,
    nickname: String,
): Boolean =
    nativeMemberNicknameIsTrustedForDisplay(userId, nickname)
        ?: error("bridge core unavailable to evaluate trusted member nickname policy")

internal fun BridgeCore.memberFieldGenericLabelPenalty(value: String): Int =
    nativeMemberGenericLabelPenalty(value)
        ?: error("bridge core unavailable to score member generic label penalty")

private fun nativeMemberLooksLikeProfileUrl(value: String): Boolean? =
    memberFieldNative("bridge-core member profile URL policy threw") {
        BridgeCoreJniMemberField.nativeMemberLooksLikeProfileUrl(value)
    }

private fun nativeMemberNicknameQualityScore(value: String): Int? =
    memberFieldNative("bridge-core member nickname quality policy threw") {
        BridgeCoreJniMemberField.nativeMemberNicknameQualityScore(value)
    }

private fun nativeMemberNicknameIsTrustedForDisplay(
    userId: Long,
    nickname: String,
): Boolean? =
    memberFieldNative("bridge-core member trusted nickname policy threw") {
        BridgeCoreJniMemberField.nativeMemberNicknameIsTrustedForDisplay(userId, nickname)
    }

private fun nativeMemberGenericLabelPenalty(value: String): Int? =
    memberFieldNative("bridge-core member generic label policy threw") {
        BridgeCoreJniMemberField.nativeMemberGenericLabelPenalty(value)
    }
