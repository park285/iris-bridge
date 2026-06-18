package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniMuxSession {
    fun nativeCreateMuxSession(maxInFlight: Int): String =
        BridgeCoreJniDispatcher.envelope(
            "mux.create",
            JSONObject().put("maxInFlight", maxInFlight),
        )

    fun nativeDestroyMuxSession(handle: Long): String =
        BridgeCoreJniDispatcher.envelope(
            "mux.destroy",
            JSONObject().put("handle", handle),
        )

    fun nativeMuxSessionOnFrame(
        handle: Long,
        frameJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "mux.onFrame",
            JSONObject()
                .put("handle", handle)
                .put("frameJson", frameJson),
        )

    fun nativeMuxSessionOnExecutorRejected(
        handle: Long,
        correlationId: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "mux.onExecutorRejected",
            JSONObject()
                .put("handle", handle)
                .put("correlationId", correlationId),
        )

    fun nativeMuxSessionOnRequestCompleted(
        handle: Long,
        correlationId: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "mux.onRequestCompleted",
            JSONObject()
                .put("handle", handle)
                .put("correlationId", correlationId),
        )

    fun nativeMuxSessionIsCancelled(
        handle: Long,
        correlationId: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "mux.isCancelled",
            JSONObject()
                .put("handle", handle)
                .put("correlationId", correlationId),
        )
}
