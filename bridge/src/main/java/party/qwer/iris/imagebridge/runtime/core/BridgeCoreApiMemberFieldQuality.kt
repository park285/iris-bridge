package party.qwer.iris.imagebridge.runtime.core

internal fun BridgeCore.memberFieldNicknameIsTrustedForDisplay(
    userId: Long,
    nickname: String,
): Boolean =
    nativeMemberNicknameIsTrustedForDisplay(userId, nickname)
        ?: error("bridge core unavailable to evaluate trusted member nickname policy")

private fun nativeMemberNicknameIsTrustedForDisplay(
    userId: Long,
    nickname: String,
): Boolean? =
    memberFieldNative("bridge-core member trusted nickname policy threw") {
        BridgeCoreJniMemberField.nativeMemberNicknameIsTrustedForDisplay(userId, nickname)
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
