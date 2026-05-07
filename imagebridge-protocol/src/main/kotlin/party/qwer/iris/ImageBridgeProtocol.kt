package party.qwer.iris

object ImageBridgeProtocol :
    ImageBridgeProtocolFrameIo,
    ImageBridgeRequestFactory,
    ImageBridgeResponseFactory {
    const val PROTOCOL_VERSION = 1
    const val ACTION_SEND_IMAGE = "send_image"
    const val ACTION_SEND_TEXT = "send_text"
    const val ACTION_SEND_MARKDOWN = "send_markdown"
    const val ACTION_HEALTH = "health"
    const val ACTION_INSPECT_CHATROOM = "inspect_chatroom"
    const val ACTION_OPEN_CHATROOM = "open_chatroom"
    const val ACTION_SNAPSHOT_CHATROOM_MEMBERS = "snapshot_chatroom_members"
    const val HANDSHAKE_HELLO = ImageBridgeHandshakeProtocol.TYPE_HELLO
    const val HANDSHAKE_SERVER_PROOF = ImageBridgeHandshakeProtocol.TYPE_SERVER_PROOF
    const val HANDSHAKE_CLIENT_PROOF = ImageBridgeHandshakeProtocol.TYPE_CLIENT_PROOF
    const val STATUS_SENT = "sent"
    const val STATUS_FAILED = "failed"
    const val STATUS_OK = "ok"
    const val MAX_FRAME_SIZE = LengthPrefixedFrameCodec.MAX_FRAME_SIZE
    const val ERROR_UNSUPPORTED_PROTOCOL = "UNSUPPORTED_PROTOCOL"
    const val ERROR_UNAUTHORIZED = "UNAUTHORIZED"
    const val ERROR_BAD_REQUEST = "BAD_REQUEST"
    const val ERROR_PATH_VALIDATION = "PATH_VALIDATION_FAILED"
    const val ERROR_BRIDGE_BUSY = "BRIDGE_BUSY"
    const val ERROR_BRIDGE_SHUTTING_DOWN = "BRIDGE_SHUTTING_DOWN"
    const val ERROR_SEND_FAILED = "SEND_FAILED"
    const val ERROR_TIMEOUT = "TIMEOUT"
    const val ERROR_INTERNAL = "INTERNAL_ERROR"
    const val ERROR_MISSING_REQUEST_ID = "MISSING_REQUEST_ID"
    const val ERROR_DUPLICATE_REQUEST = "DUPLICATE_REQUEST"
    const val ERROR_CANCELLED = "CANCELLED"

    typealias ImageBridgeRequest = party.qwer.iris.ImageBridgeRequest
    typealias ChatRoomMemberHint = party.qwer.iris.ChatRoomMemberHint
    typealias ChatRoomSnapshotConfidence = party.qwer.iris.ChatRoomSnapshotConfidence
    typealias ImageBridgeCapability = party.qwer.iris.ImageBridgeCapability
    typealias ImageBridgeCapabilities = party.qwer.iris.ImageBridgeCapabilities
    typealias ChatRoomMemberExtractionPlan = party.qwer.iris.ChatRoomMemberExtractionPlan
    typealias ImageBridgeCheck = party.qwer.iris.ImageBridgeCheck
    typealias ImageBridgeDiscoveryHook = party.qwer.iris.ImageBridgeDiscoveryHook
    typealias ImageBridgeDiscovery = party.qwer.iris.ImageBridgeDiscovery
    typealias ChatRoomMemberSnapshot = party.qwer.iris.ChatRoomMemberSnapshot
    typealias ChatRoomMembersSnapshot = party.qwer.iris.ChatRoomMembersSnapshot
    typealias ImageBridgeResponse = party.qwer.iris.ImageBridgeResponse
    typealias ImageBridgeMetrics = party.qwer.iris.ImageBridgeMetrics
}
