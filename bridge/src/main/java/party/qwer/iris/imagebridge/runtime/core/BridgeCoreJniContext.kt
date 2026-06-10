package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniContext {
    external fun nativeCreateContext(
        mode: String?,
        token: String,
        requireHandshakeRaw: String?,
    ): Long

    external fun nativeDestroyContext(handle: Long)

    external fun nativeHandshakeOnHello(
        handle: Long,
        frameJson: String,
        nowMs: Long,
        socketName: String,
    ): String

    external fun nativeHandshakeOnClientProof(
        handle: Long,
        frameJson: String,
    ): String

    external fun nativeRequireHandshake(handle: Long): Boolean

    external fun nativeAbiVersion(): Int
}
