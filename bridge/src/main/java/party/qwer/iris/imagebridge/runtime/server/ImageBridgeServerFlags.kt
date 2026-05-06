package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.resolveBridgeMuxServerEnabled
import party.qwer.iris.resolveBridgeTextSendMarkdownEnabled
import party.qwer.iris.resolveBridgeTextSendTextEnabled

internal fun textBridgeSendTextEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED")): Boolean = raw?.let(::isBridgeTruthy) ?: resolveBridgeTextSendTextEnabled()

internal fun textBridgeSendMarkdownEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED")): Boolean = raw?.let(::isBridgeTruthy) ?: resolveBridgeTextSendMarkdownEnabled()

internal fun muxBridgeServerEnabled(raw: String? = System.getenv("IRIS_BRIDGE_MUX_SERVER_ENABLED")): Boolean = raw?.let(::isBridgeTruthy) ?: resolveBridgeMuxServerEnabled()

private fun isBridgeTruthy(raw: String): Boolean =
    when (raw.trim().lowercase()) {
        "true", "1", "on", "yes" -> true
        else -> false
    }
