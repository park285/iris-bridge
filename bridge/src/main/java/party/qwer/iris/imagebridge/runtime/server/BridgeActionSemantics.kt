package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

internal fun String.isBridgeSideEffectAction(): Boolean =
    this == ImageBridgeProtocol.ACTION_SEND_IMAGE ||
        this == ImageBridgeProtocol.ACTION_SEND_TEXT ||
        this == ImageBridgeProtocol.ACTION_SEND_MARKDOWN ||
        this == ImageBridgeProtocol.ACTION_OPEN_CHATROOM

internal fun String.requiresBridgeRequestId(): Boolean =
    this == ImageBridgeProtocol.ACTION_SEND_IMAGE ||
        this == ImageBridgeProtocol.ACTION_SEND_TEXT ||
        this == ImageBridgeProtocol.ACTION_SEND_MARKDOWN ||
        this == ImageBridgeProtocol.ACTION_OPEN_CHATROOM
