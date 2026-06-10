package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.loadOrNull

internal class BridgeTextRequestValidator private constructor(
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
) {
    constructor(
        expectedToken: String = party.qwer.iris.resolveBridgeToken(),
        securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
    ) : this(bridgeCoreProviderFor(expectedToken, securityMode))

    constructor(bridgeCore: BridgeCoreRuntime) : this({ bridgeCore })

    fun validate(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        markdown: Boolean,
    ): String? {
        val core = bridgeCoreProvider() ?: throw IllegalArgumentException("bridge core unavailable")
        val envelope =
            core.validateTextRequest(
                roomId = request.roomId,
                message = request.message,
                markdown = markdown,
                attachmentJson = request.attachmentJson,
                mentionsJson = request.mentionsJson,
            )
        if (!envelope.isOk) {
            throw IllegalArgumentException(envelope.errorMessage ?: "text request rejected")
        }
        return envelope.string("attachmentJson")
    }
}

private fun bridgeCoreProviderFor(
    expectedToken: String,
    securityMode: BridgeSecurityMode,
): () -> BridgeCoreRuntime? {
    val runtime by lazy {
        BridgeCore.loadOrNull(
            securityMode = securityMode.coreRawValue(),
            bridgeToken = expectedToken,
            requireHandshakeRaw = null,
        )
    }
    return { runtime }
}
