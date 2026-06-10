package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniPolicy {
    external fun nativeIsTruthyFlag(raw: String): Boolean

    external fun nativeNormalizeSecurityMode(raw: String?): String

    external fun nativeAllowedPeerUids(
        securityModeRaw: String?,
        extraUidsRaw: String?,
    ): IntArray

    external fun nativeSendBlockReason(
        installAttempted: Boolean,
        hooksJson: String,
        imageCount: Int,
        threadId: Long,
        hasThreadId: Boolean,
        threadScope: Int,
        hasThreadScope: Boolean,
    ): String

    external fun nativeCurrentBridgeCapabilities(
        registryAvailable: Boolean,
        registryError: String?,
        specReady: Boolean,
        textSupported: Boolean,
        textReady: Boolean,
        textReason: String?,
        sendTextEnabled: Boolean,
        sendMarkdownEnabled: Boolean,
    ): String

    external fun nativeServerRestartDelayMs(failureCount: Int): Long
}
