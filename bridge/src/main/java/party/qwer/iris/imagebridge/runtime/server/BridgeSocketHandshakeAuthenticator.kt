package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.LengthPrefixedFrameCodec
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import java.io.InputStream
import java.io.OutputStream

internal class BridgeSocketHandshakeAuthenticator private constructor(
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
) {
    constructor(
        expectedToken: String = party.qwer.iris.resolveBridgeToken(),
        securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
        requireHandshakeRaw: String? = System.getenv("IRIS_BRIDGE_REQUIRE_HANDSHAKE"),
    ) : this(bridgeCoreProviderFor(expectedToken, securityMode, requireHandshakeRaw))

    constructor(bridgeCore: BridgeCoreRuntime) : this({ bridgeCore })

    fun authenticate(
        input: InputStream,
        output: OutputStream,
        socketName: String,
    ) {
        val core = bridgeCoreProvider() ?: throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED)
        if (!core.requireHandshake) return
        try {
            val helloPayload = LengthPrefixedFrameCodec.readPayload(input)
            val helloEnvelope = core.handshakeOnHello(helloPayload, System.currentTimeMillis())
            val serverFrameJson =
                helloEnvelope.string("frameJson")?.takeIf { helloEnvelope.isOk }
                    ?: throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED)
            LengthPrefixedFrameCodec.writePayload(output, serverFrameJson)
            val clientProofPayload = LengthPrefixedFrameCodec.readPayload(input)
            val proofEnvelope = core.handshakeOnClientProof(clientProofPayload)
            if (!proofEnvelope.isOk) {
                throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED)
            }
        } catch (error: Exception) {
            throw IllegalArgumentException(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED, error)
        }
    }
}

private fun bridgeCoreProviderFor(
    expectedToken: String,
    securityMode: BridgeSecurityMode,
    requireHandshakeRaw: String?,
): () -> BridgeCoreRuntime? {
    val runtime by lazy {
        BridgeCore.loadOrNull(
            securityMode = securityMode.coreRawValue(),
            bridgeToken = expectedToken,
            requireHandshakeRaw = requireHandshakeRaw,
        )
    }
    return { runtime }
}
