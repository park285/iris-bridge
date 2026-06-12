package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniMemberField {
    external fun nativeMemberNicknameIsTrustedForDisplay(
        userId: Long,
        nickname: String,
    ): Boolean

    external fun nativeMemberExtractionEvaluate(requestJson: String): String
}
