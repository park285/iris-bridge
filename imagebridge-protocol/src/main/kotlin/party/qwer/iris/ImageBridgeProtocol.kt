package party.qwer.iris

import party.qwer.iris.generated.GeneratedBridgeProtocolContract

object ImageBridgeProtocol :
    ImageBridgeProtocolFrameIo,
    ImageBridgeRequestFactory,
    ImageBridgeResponseFactory {
    const val PROTOCOL_VERSION = GeneratedBridgeProtocolContract.PROTOCOL_VERSION
    const val ACTION_SEND_IMAGE = GeneratedBridgeProtocolContract.ACTION_SEND_IMAGE
    const val ACTION_SEND_TEXT = GeneratedBridgeProtocolContract.ACTION_SEND_TEXT
    const val ACTION_SEND_MARKDOWN = GeneratedBridgeProtocolContract.ACTION_SEND_MARKDOWN
    const val ACTION_HEALTH = GeneratedBridgeProtocolContract.ACTION_HEALTH
    const val ACTION_INSPECT_CHATROOM = GeneratedBridgeProtocolContract.ACTION_INSPECT_CHATROOM
    const val ACTION_OPEN_CHATROOM = GeneratedBridgeProtocolContract.ACTION_OPEN_CHATROOM
    const val ACTION_SNAPSHOT_CHATROOM_MEMBERS = GeneratedBridgeProtocolContract.ACTION_SNAPSHOT_CHATROOM_MEMBERS
    const val ACTION_FETCH_MEMBER_PROFILES = GeneratedBridgeProtocolContract.ACTION_FETCH_MEMBER_PROFILES
    const val HANDSHAKE_HELLO = GeneratedBridgeProtocolContract.HANDSHAKE_FRAME_TYPE_HELLO
    const val HANDSHAKE_SERVER_PROOF = GeneratedBridgeProtocolContract.HANDSHAKE_FRAME_TYPE_SERVER_PROOF
    const val HANDSHAKE_CLIENT_PROOF = GeneratedBridgeProtocolContract.HANDSHAKE_FRAME_TYPE_CLIENT_PROOF
    const val STATUS_SENT = GeneratedBridgeProtocolContract.STATUS_SENT
    const val STATUS_FAILED = GeneratedBridgeProtocolContract.STATUS_FAILED
    const val STATUS_OK = GeneratedBridgeProtocolContract.STATUS_OK
    const val MAX_FRAME_SIZE = GeneratedBridgeProtocolContract.MAX_FRAME_SIZE
    const val ERROR_UNSUPPORTED_PROTOCOL = GeneratedBridgeProtocolContract.ERROR_UNSUPPORTED_PROTOCOL
    const val ERROR_UNAUTHORIZED = GeneratedBridgeProtocolContract.ERROR_UNAUTHORIZED
    const val ERROR_BAD_REQUEST = GeneratedBridgeProtocolContract.ERROR_BAD_REQUEST
    const val ERROR_PATH_VALIDATION = GeneratedBridgeProtocolContract.ERROR_PATH_VALIDATION_FAILED
    const val ERROR_BRIDGE_BUSY = GeneratedBridgeProtocolContract.ERROR_BRIDGE_BUSY
    const val ERROR_BRIDGE_SHUTTING_DOWN = GeneratedBridgeProtocolContract.ERROR_BRIDGE_SHUTTING_DOWN
    const val ERROR_SEND_FAILED = GeneratedBridgeProtocolContract.ERROR_SEND_FAILED
    const val ERROR_TIMEOUT = GeneratedBridgeProtocolContract.ERROR_TIMEOUT
    const val ERROR_INTERNAL = GeneratedBridgeProtocolContract.ERROR_INTERNAL_ERROR
    const val ERROR_MISSING_REQUEST_ID = GeneratedBridgeProtocolContract.ERROR_MISSING_REQUEST_ID
    const val ERROR_DUPLICATE_REQUEST = GeneratedBridgeProtocolContract.ERROR_DUPLICATE_REQUEST
    const val ERROR_CANCELLED = GeneratedBridgeProtocolContract.ERROR_CANCELLED

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
