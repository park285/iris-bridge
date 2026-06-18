package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniContext {
    fun nativeCreateContext(
        mode: String?,
        token: String,
        requireHandshakeRaw: String?,
    ): Long =
        BridgeCoreJniDispatcher
            .parsed(
                "context.create",
                JSONObject()
                    .putNullable("mode", mode)
                    .put("token", token)
                    .putNullable("requireHandshakeRaw", requireHandshakeRaw),
            ).long("handle") ?: 0L

    fun nativeDestroyContext(handle: Long) {
        BridgeCoreJniDispatcher.envelope("context.destroy", JSONObject().put("handle", handle))
    }

    fun nativeHandshakeOnHello(
        handle: Long,
        frameJson: String,
        nowMs: Long,
        socketName: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "context.handshakeOnHello",
            JSONObject()
                .put("handle", handle)
                .put("frameJson", frameJson)
                .put("nowMs", nowMs)
                .put("socketName", socketName),
        )

    fun nativeHandshakeOnClientProof(
        handle: Long,
        frameJson: String,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "context.handshakeOnClientProof",
            JSONObject()
                .put("handle", handle)
                .put("frameJson", frameJson),
        )

    fun nativeRequireHandshake(handle: Long): Boolean =
        BridgeCoreJniDispatcher.booleanValue(
            "context.requireHandshake",
            JSONObject().put("handle", handle),
            default = true,
        )

    fun nativeAbiVersion(): Int = BridgeCoreJniDispatcher.intValue("context.abiVersion", default = -1)
}
