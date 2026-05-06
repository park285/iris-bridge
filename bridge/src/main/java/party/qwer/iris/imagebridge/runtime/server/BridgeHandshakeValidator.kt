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
        validateToken(request.token)
    }

    private fun validateToken(actualToken: String?) {
        when (securityMode) {
            BridgeSecurityMode.PRODUCTION -> requireProductionToken(actualToken)
            BridgeSecurityMode.DEVELOPMENT -> requireDevelopmentToken(actualToken)
        }
    }

    private fun requireProductionToken(actualToken: String?) {
        require(expectedToken.isNotBlank()) { "bridge token must be configured in production mode" }
        require(tokensMatch(actualToken, expectedToken)) { "unauthorized bridge token" }
    }

    private fun requireDevelopmentToken(actualToken: String?) {
        if (expectedToken.isBlank()) return
        require(tokensMatch(actualToken, expectedToken)) { "unauthorized bridge token" }
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
