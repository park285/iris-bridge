package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.discovery.sendBlockReason
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor

internal class ImageBridgeRequestHandler(
    private val imageSender: (ImageSendRequest) -> Unit,
    private val healthProvider: () -> ImageBridgeHealthSnapshot,
    private val chatRoomInspector: ((Long) -> String)? = null,
    private val chatRoomOpener: ((Long) -> Unit)? = null,
    private val chatRoomMemberSnapshotProvider: ((Long, List<ImageBridgeProtocol.ChatRoomMemberHint>, ImageBridgeProtocol.ChatRoomMemberExtractionPlan?) -> ImageBridgeProtocol.ChatRoomMembersSnapshot)? = null,
    private val handshakeValidator: BridgeHandshakeValidator = BridgeHandshakeValidator(),
    private val serialExecutor: RoomThreadSerialExecutor = RoomThreadSerialExecutor(),
    private val pathValidator: BridgeImagePathValidator = BridgeImagePathValidator(),
    private val metrics: BridgeMetrics = BridgeMetrics(),
    private val logError: (String, String, Throwable) -> Unit = { tag, message, error -> Log.e(tag, message, error) },
) {
    fun handle(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse =
        try {
            handshakeValidator.validate(request)
            when (val action = request.action) {
                ImageBridgeProtocol.ACTION_SEND_IMAGE -> handleSendImage(request)
                ImageBridgeProtocol.ACTION_HEALTH -> healthProvider().toProtocolResponse()
                ImageBridgeProtocol.ACTION_INSPECT_CHATROOM -> handleInspectChatRoom(request)
                ImageBridgeProtocol.ACTION_OPEN_CHATROOM -> handleOpenChatRoom(request)
                ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS -> handleSnapshotChatRoomMembers(request)
                else ->
                    bridgeFailureResponse(
                        error = "unknown action: $action",
                        errorCode = ImageBridgeProtocol.ERROR_BAD_REQUEST,
                        requestId = request.requestId,
                    )
            }
        } catch (e: Exception) {
            metrics.recordFailure(bridgeErrorCodeFor(e))
            logFailure(request, e)
            bridgeFailureResponse(
                error = e.message ?: "internal error",
                errorCode = bridgeErrorCodeFor(e),
                requestId = request.requestId,
            )
        }

    private fun handleSendImage(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val health = healthProvider()
        check(health.specStatus.ready) { "bridge spec not ready" }
        val imageRequest =
            ImageSendRequest(
                roomId = checkNotNull(request.roomId) { "roomId missing" },
                imagePaths = pathValidator.validate(request.imagePaths),
                threadId = request.threadId,
                threadScope = request.threadScope,
                requestId = request.requestId,
            )
        health.discoverySnapshot
            .sendBlockReason(
                imageCount = imageRequest.imagePaths.size,
                threadId = imageRequest.threadId,
                threadScope = imageRequest.threadScope,
            )?.let { reason ->
                error(reason)
            }
        val startedAtEpochMs = System.currentTimeMillis()
        metrics.recordSendStart(imageRequest.requestId, startedAtEpochMs)
        serialExecutor.executeSynchronously(imageRequest.roomId, imageRequest.threadId) {
            imageSender(imageRequest)
        }
        val completedAtEpochMs = System.currentTimeMillis()
        metrics.recordSendSuccess(completedAtEpochMs, completedAtEpochMs - startedAtEpochMs)
        return ImageBridgeProtocol.buildSuccessResponse(request.requestId)
    }

    private fun handleInspectChatRoom(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val inspector = checkNotNull(chatRoomInspector) { "chatroom inspection unavailable" }
        return ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK, inspectionJson = inspector(roomId))
    }

    private fun handleOpenChatRoom(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val opener = checkNotNull(chatRoomOpener) { "chatroom opener unavailable" }
        opener(roomId)
        return ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK)
    }

    private fun handleSnapshotChatRoomMembers(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val provider = checkNotNull(chatRoomMemberSnapshotProvider) { "chatroom member snapshot unavailable" }
        return ImageBridgeProtocol.ImageBridgeResponse(
            status = ImageBridgeProtocol.STATUS_OK,
            memberSnapshot =
                provider(
                    roomId,
                    request.memberHints.ifEmpty {
                        request.memberIds
                            .distinct()
                            .sorted()
                            .map { userId -> ImageBridgeProtocol.ChatRoomMemberHint(userId = userId) }
                    },
                    request.preferredMemberPlan,
                ),
        )
    }

    private fun logFailure(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        error: Exception,
    ) {
        val action = request.action.ifBlank { "<missing>" }
        val roomId = request.roomId?.toString() ?: "<missing>"
        val requestId = request.requestId ?: "<missing>"
        runCatching {
            logError(BRIDGE_LOG_TAG, "request handling failed action=$action roomId=$roomId requestId=$requestId", error)
        }
    }
}
