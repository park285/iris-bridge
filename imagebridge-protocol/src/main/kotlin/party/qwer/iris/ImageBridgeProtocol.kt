package party.qwer.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object ImageBridgeProtocol {
    const val PROTOCOL_VERSION = 1
    const val SOCKET_NAME = IrisRuntimePathPolicy.DEFAULT_IMAGE_BRIDGE_SOCKET_NAME
    const val ACTION_SEND_IMAGE = "send_image"
    const val ACTION_SEND_TEXT = "send_text"
    const val ACTION_SEND_MARKDOWN = "send_markdown"
    const val ACTION_HEALTH = "health"
    const val ACTION_INSPECT_CHATROOM = "inspect_chatroom"
    const val ACTION_OPEN_CHATROOM = "open_chatroom"
    const val ACTION_SNAPSHOT_CHATROOM_MEMBERS = "snapshot_chatroom_members"
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

    private val serializerJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    @Serializable
    data class ImageBridgeRequest(
        val action: String,
        val protocolVersion: Int? = null,
        val roomId: Long? = null,
        val imagePaths: List<String> = emptyList(),
        val message: String? = null,
        val markdown: Boolean? = null,
        val mentionsJson: String? = null,
        val threadId: Long? = null,
        val threadScope: Int? = null,
        val requestId: String? = null,
        val token: String? = null,
        val memberIds: List<Long> = emptyList(),
        val memberHints: List<ChatRoomMemberHint> = emptyList(),
        val preferredMemberPlan: ChatRoomMemberExtractionPlan? = null,
    )

    @Serializable
    data class ChatRoomMemberHint(
        val userId: Long,
        val nickname: String? = null,
    )

    @Serializable
    enum class ChatRoomSnapshotConfidence {
        HIGH,
        MEDIUM,
        LOW,
    }

    @Serializable
    data class ImageBridgeCapability(
        val supported: Boolean = false,
        val ready: Boolean = false,
        val reason: String? = null,
    )

    @Serializable
    data class ImageBridgeCapabilities(
        val inspectChatRoom: ImageBridgeCapability = ImageBridgeCapability(),
        val openChatRoom: ImageBridgeCapability = ImageBridgeCapability(),
        val snapshotChatRoomMembers: ImageBridgeCapability = ImageBridgeCapability(),
        val sendText: ImageBridgeCapability = ImageBridgeCapability(),
        val sendMarkdown: ImageBridgeCapability = ImageBridgeCapability(),
    )

    @Serializable
    data class ChatRoomMemberExtractionPlan(
        val containerPath: String,
        val sourceClassName: String? = null,
        val userIdPath: String,
        val nicknamePath: String,
        val rolePath: String? = null,
        val profileImagePath: String? = null,
        val fingerprint: String,
        val version: Int = 1,
    )

    @Serializable
    data class ImageBridgeCheck(
        val name: String,
        val ok: Boolean,
        val detail: String? = null,
    )

    @Serializable
    data class ImageBridgeDiscoveryHook(
        val name: String,
        val installed: Boolean,
        val installError: String? = null,
        val invocationCount: Int = 0,
        val lastSeenEpochMs: Long? = null,
        val lastSummary: String? = null,
    )

    @Serializable
    data class ImageBridgeDiscovery(
        val installAttempted: Boolean = false,
        val hooks: List<ImageBridgeDiscoveryHook> = emptyList(),
    )

    @Serializable
    data class ChatRoomMemberSnapshot(
        val userId: Long,
        val nickname: String,
        val roleCode: Int? = null,
        val profileImageUrl: String? = null,
    )

    @Serializable
    data class ChatRoomMembersSnapshot(
        val roomId: Long,
        val sourcePath: String? = null,
        val sourceClassName: String? = null,
        val scannedAtEpochMs: Long,
        val members: List<ChatRoomMemberSnapshot> = emptyList(),
        val selectedPlan: ChatRoomMemberExtractionPlan? = null,
        val confidence: ChatRoomSnapshotConfidence = ChatRoomSnapshotConfidence.LOW,
        val confidenceScore: Int = 0,
        val usedPreferredPlan: Boolean = false,
        val candidateGap: Int? = null,
    )

    @Serializable
    data class ImageBridgeResponse(
        val status: String,
        val error: String? = null,
        val errorCode: String? = null,
        val requestId: String? = null,
        val running: Boolean? = null,
        val specReady: Boolean? = null,
        val checkedAtEpochMs: Long? = null,
        val restartCount: Int? = null,
        val lastCrashMessage: String? = null,
        val checks: List<ImageBridgeCheck> = emptyList(),
        val discovery: ImageBridgeDiscovery? = null,
        val inspectionJson: String? = null,
        val memberSnapshot: ChatRoomMembersSnapshot? = null,
        val capabilities: ImageBridgeCapabilities? = null,
        val metrics: ImageBridgeMetrics? = null,
    )

    @Serializable
    data class ImageBridgeMetrics(
        val sendSuccess: Long = 0,
        val sendFailure: Long = 0,
        val pathValidationFailure: Long = 0,
        val unauthorizedClient: Long = 0,
        val bridgeBusy: Long = 0,
        val bridgeShuttingDown: Long = 0,
        val timeout: Long = 0,
        val missingRequestId: Long = 0,
        val rejectedClient: Long = 0,
        val activeClient: Long = 0,
        val queuedClient: Long = 0,
        val lastSendRequestId: String? = null,
        val lastSendStartedAtEpochMs: Long? = null,
        val lastSendCompletedAtEpochMs: Long? = null,
        val lastSendDurationMs: Long? = null,
        val lastSendErrorCode: String? = null,
    )

    fun writeFrame(
        output: OutputStream,
        request: ImageBridgeRequest,
    ) = writeFramePayload(output, serializerJson.encodeToString(request))

    fun writeFrame(
        output: OutputStream,
        response: ImageBridgeResponse,
    ) = writeFramePayload(output, serializerJson.encodeToString(response))

    fun readRequestFrame(input: InputStream): ImageBridgeRequest = serializerJson.decodeFromString(readFramePayload(input))

    fun readResponseFrame(input: InputStream): ImageBridgeResponse = serializerJson.decodeFromString(readFramePayload(input))

    fun buildSendImageRequest(
        roomId: Long,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_SEND_IMAGE,
            protocolVersion = PROTOCOL_VERSION,
            roomId = roomId,
            imagePaths = imagePaths,
            threadId = threadId,
            threadScope = threadScope,
            requestId = requestId,
            token = token,
        )

    fun buildSendTextRequest(
        roomId: Long,
        message: String,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String? = null,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_SEND_TEXT,
            protocolVersion = PROTOCOL_VERSION,
            roomId = roomId,
            message = message,
            markdown = false,
            mentionsJson = mentionsJson,
            threadId = threadId,
            threadScope = threadScope,
            requestId = requestId,
            token = token,
        )

    fun buildSendMarkdownRequest(
        roomId: Long,
        message: String,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String? = null,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_SEND_MARKDOWN,
            protocolVersion = PROTOCOL_VERSION,
            roomId = roomId,
            message = message,
            markdown = true,
            mentionsJson = mentionsJson,
            threadId = threadId,
            threadScope = threadScope,
            requestId = requestId,
            token = token,
        )

    fun buildHealthRequest(token: String? = null): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_HEALTH,
            protocolVersion = PROTOCOL_VERSION,
            token = token,
        )

    fun buildInspectChatRoomRequest(
        roomId: Long,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_INSPECT_CHATROOM,
            protocolVersion = PROTOCOL_VERSION,
            roomId = roomId,
            token = token,
        )

    fun buildOpenChatRoomRequest(
        roomId: Long,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_OPEN_CHATROOM,
            protocolVersion = PROTOCOL_VERSION,
            roomId = roomId,
            token = token,
        )

    fun buildSnapshotChatRoomMembersRequest(
        roomId: Long,
        memberIds: List<Long> = emptyList(),
        memberHints: List<ChatRoomMemberHint> = emptyList(),
        preferredMemberPlan: ChatRoomMemberExtractionPlan? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ACTION_SNAPSHOT_CHATROOM_MEMBERS,
            protocolVersion = PROTOCOL_VERSION,
            roomId = roomId,
            token = token,
            memberIds = memberIds,
            memberHints = memberHints,
            preferredMemberPlan = preferredMemberPlan,
        )

    fun buildSuccessResponse(requestId: String? = null): ImageBridgeResponse =
        ImageBridgeResponse(
            status = STATUS_SENT,
            requestId = requestId,
        )

    fun buildFailureResponse(
        error: String,
        errorCode: String? = null,
        requestId: String? = null,
    ): ImageBridgeResponse =
        ImageBridgeResponse(
            status = STATUS_FAILED,
            error = error,
            errorCode = errorCode,
            requestId = requestId,
        )

    private fun writeFramePayload(
        output: OutputStream,
        payload: String,
    ) = LengthPrefixedFrameCodec.writePayload(output, payload)

    private fun readFramePayload(input: InputStream): String = LengthPrefixedFrameCodec.readPayload(input)
}
