package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.loadOrNull

internal class BridgeImagePathStaticValidator private constructor(
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
) {
    constructor(
        expectedToken: String = party.qwer.iris.resolveBridgeToken(),
        securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
    ) : this(bridgeCoreProviderFor(expectedToken, securityMode))

    constructor(bridgeCore: BridgeCoreRuntime) : this({ bridgeCore })

    fun validate(
        imagePaths: List<String>,
        maxPathCount: Int,
        maxPathLength: Int,
    ) {
        val core = bridgeCoreProvider() ?: throw IllegalArgumentException("bridge core unavailable")
        val envelope = core.validateImagePaths(imagePaths, maxPathCount, maxPathLength)
        if (!envelope.isOk) {
            throw IllegalArgumentException(envelope.errorMessage ?: "image path validation failed")
        }
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
