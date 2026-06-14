package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.loadOrNull

internal class BridgeHandshakeValidator private constructor(
    private val bridgeCoreProvider: () -> BridgeCoreRuntime?,
) {
    constructor(
        expectedToken: String = party.qwer.iris.resolveBridgeToken(),
        securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
    ) : this(bridgeCoreProviderFor(expectedToken, securityMode))

    constructor(bridgeCore: BridgeCoreRuntime) : this({ bridgeCore })

    fun validate(request: ImageBridgeProtocol.ImageBridgeRequest) {
        val core = bridgeCoreProvider() ?: throw IllegalArgumentException("bridge core unavailable")
        val envelope =
            core.validateRequestToken(
                JSONObject()
                    .put("protocolVersion", request.protocolVersion ?: JSONObject.NULL)
                    .put("action", request.action)
                    .put("token", request.token ?: JSONObject.NULL)
                    .toString(),
            )
        if (!envelope.isOk) {
            throw IllegalArgumentException(envelope.errorMessage ?: "bridge token rejected")
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

internal fun BridgeSecurityMode.coreRawValue(): String =
    when (this) {
        BridgeSecurityMode.DEVELOPMENT -> "development"
        BridgeSecurityMode.PRODUCTION -> "production"
    }
