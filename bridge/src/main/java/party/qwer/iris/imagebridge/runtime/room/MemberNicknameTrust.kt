package party.qwer.iris.imagebridge.runtime.room

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.memberFieldNicknameIsTrustedForDisplay

internal fun memberNicknameNeedsUpstream(
    userId: Long,
    nickname: String?,
): Boolean {
    if (userId <= 0L) {
        return false
    }
    val trimmed = nickname?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return true
    }
    return !BridgeCore.memberFieldNicknameIsTrustedForDisplay(userId, trimmed)
}
