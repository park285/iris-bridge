package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.normalizeSecurityMode

internal enum class BridgeSecurityMode {
    DEVELOPMENT,
    PRODUCTION,
    ;

    companion object {
        fun fromEnv(raw: String? = System.getenv("IRIS_BRIDGE_SECURITY_MODE")): BridgeSecurityMode =
            when (BridgeCore.normalizeSecurityMode(raw)) {
                "development" -> DEVELOPMENT
                else -> PRODUCTION
            }
    }
}
