package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.isTruthyFlag
import party.qwer.iris.resolveBridgeTextSendMarkdownEnabled
import party.qwer.iris.resolveBridgeTextSendTextEnabled

internal fun textBridgeSendTextEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED")): Boolean =
    raw?.let(BridgeCore::isTruthyFlag) ?: resolveBridgeTextSendTextEnabled()

internal fun textBridgeSendMarkdownEnabled(raw: String? = System.getenv("IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED")): Boolean =
    raw?.let(BridgeCore::isTruthyFlag) ?: resolveBridgeTextSendMarkdownEnabled()
