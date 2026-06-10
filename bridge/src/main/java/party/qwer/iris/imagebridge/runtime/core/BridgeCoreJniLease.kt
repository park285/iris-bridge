package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniLease {
    external fun nativeVerifyLeases(
        handle: Long,
        roomId: Long,
        requestId: String,
        leasesJson: String,
        factsJson: String,
        nowMs: Long,
    ): String

    external fun nativeBuildImageLeaseFacts(canonicalPathsJson: String): String

    external fun nativeImageLeaseRejectionIsStateError(message: String): Boolean

    external fun nativeDedupeAdmit(
        handle: Long,
        key: String,
        nowMs: Long,
    ): String

    external fun nativeDedupeComplete(
        handle: Long,
        key: String,
        responseJson: String,
        nowMs: Long,
    )
}
