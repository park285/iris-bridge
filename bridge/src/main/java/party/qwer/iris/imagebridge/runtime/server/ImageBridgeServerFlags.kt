package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.resolveBridgeTextSendMarkdownEnabled
import party.qwer.iris.resolveBridgeTextSendTextEnabled

internal fun textBridgeSendTextEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED")): Boolean = raw?.let(::isBridgeTruthy) ?: resolveBridgeTextSendTextEnabled()

internal fun textBridgeSendMarkdownEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED")): Boolean = raw?.let(::isBridgeTruthy) ?: resolveBridgeTextSendMarkdownEnabled()

private fun isBridgeTruthy(raw: String): Boolean =
    when (raw.trim().lowercase()) {
        "true", "1", "on", "yes" -> true
        else -> false
    }
