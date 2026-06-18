package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniLease {
    fun nativeVerifyLeases(
        handle: Long,
        roomId: Long,
        requestId: String,
        leasesJson: String,
        factsJson: String,
        nowMs: Long,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "lease.verify",
            JSONObject()
                .put("handle", handle)
                .put("roomId", roomId)
                .put("requestId", requestId)
                .put("leasesJson", leasesJson)
                .put("factsJson", factsJson)
                .put("nowMs", nowMs),
        )

    fun nativeBuildImageLeaseFacts(canonicalPathsJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "lease.buildImageFacts",
            JSONObject().put("canonicalPathsJson", canonicalPathsJson),
        )

    fun nativeImageLeaseRejectionIsStateError(message: String): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "lease.rejectionIsStateError",
            JSONObject().put("message", message),
        )

    fun nativeDedupeAdmit(
        handle: Long,
        key: String,
        nowMs: Long,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "lease.dedupeAdmit",
            JSONObject()
                .put("handle", handle)
                .put("key", key)
                .put("nowMs", nowMs),
        )

    fun nativeDedupeComplete(
        handle: Long,
        key: String,
        responseJson: String,
        nowMs: Long,
    ) {
        BridgeCoreJniDispatcher.envelope(
            "lease.dedupeComplete",
            JSONObject()
                .put("handle", handle)
                .put("key", key)
                .put("responseJson", responseJson)
                .put("nowMs", nowMs),
        )
    }
}
