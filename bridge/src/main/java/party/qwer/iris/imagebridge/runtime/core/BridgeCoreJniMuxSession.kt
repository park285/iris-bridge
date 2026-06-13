package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniMuxSession {
    external fun nativeCreateMuxSession(maxInFlight: Int): String

    external fun nativeDestroyMuxSession(handle: Long): String

    external fun nativeMuxSessionOnFrame(
        handle: Long,
        frameJson: String,
    ): String

    external fun nativeMuxSessionOnExecutorRejected(
        handle: Long,
        correlationId: String,
    ): String

    external fun nativeMuxSessionOnRequestCompleted(
        handle: Long,
        correlationId: String,
    ): String

    external fun nativeMuxSessionIsCancelled(
        handle: Long,
        correlationId: String,
    ): String
}
