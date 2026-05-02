package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class BridgeHandshakeValidator(
    private val expectedToken: String = party.qwer.iris.resolveBridgeToken(),
    private val securityMode: BridgeSecurityMode = BridgeSecurityMode.fromEnv(),
) {
    fun validate(request: ImageBridgeProtocol.ImageBridgeRequest) {
        require(request.protocolVersion == ImageBridgeProtocol.PROTOCOL_VERSION) {
            "unsupported protocol version"
        }
        when (securityMode) {
            BridgeSecurityMode.PRODUCTION -> {
                require(expectedToken.isNotBlank()) {
                    "bridge token must be configured in production mode"
                }
                require(tokensMatch(request.token, expectedToken)) { "unauthorized bridge token" }
            }
            BridgeSecurityMode.DEVELOPMENT -> {
                if (expectedToken.isNotBlank()) {
                    require(tokensMatch(request.token, expectedToken)) { "unauthorized bridge token" }
                }
            }
        }
    }

    private fun tokensMatch(
        actual: String?,
        expected: String,
    ): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val actualDigest = digest.digest((actual ?: "").toByteArray(StandardCharsets.UTF_8))
        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(expected.toByteArray(StandardCharsets.UTF_8))
        return MessageDigest.isEqual(actualDigest, expectedDigest)
    }
}
