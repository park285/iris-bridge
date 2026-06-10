package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor
import party.qwer.iris.imagebridge.runtime.send.TextSendRequest

internal class ImageBridgeRequestHandler(
    private val imageSender: (ImageSendRequest) -> Unit,
    private val textSender: ((TextSendRequest) -> Unit)? = null,
    private val healthProvider: () -> ImageBridgeHealthSnapshot,
    private val chatRoomInspector: ((Long) -> String)? = null,
    private val chatRoomOpener: ((Long) -> Unit)? = null,
    private val chatRoomMemberSnapshotProvider: ((Long, List<ImageBridgeProtocol.ChatRoomMemberHint>, ImageBridgeProtocol.ChatRoomMemberExtractionPlan?) -> ImageBridgeProtocol.ChatRoomMembersSnapshot)? = null,
    private val handshakeValidator: BridgeHandshakeValidator = BridgeHandshakeValidator(),
    private val serialExecutor: RoomThreadSerialExecutor = RoomThreadSerialExecutor(),
    private val pathValidator: BridgeImagePathValidator = BridgeImagePathValidator(),
    private val metrics: BridgeMetrics = BridgeMetrics(),
    private val leaseVerifier: BridgeImageLeaseVerifier = BridgeImageLeaseVerifier(),
    private val deduper: BridgeRequestDeduper = BridgeRequestDeduper(onDedupeHit = { metrics.recordMuxRequestDeduplicated() }),
    private val textRequestValidator: BridgeTextRequestValidator = BridgeTextRequestValidator(),
    private val logError: (String, String, Throwable) -> Unit = { tag, message, error -> Log.e(tag, message, error) },
) {
    private val imageActionHandler =
        BridgeImageActionHandler(
            imageSender = imageSender,
            serialExecutor = serialExecutor,
            pathValidator = pathValidator,
            metrics = metrics,
            leaseVerifier = leaseVerifier,
        )
    private val textActionHandler =
        BridgeTextActionHandler(
            textSender = textSender,
            serialExecutor = serialExecutor,
            metrics = metrics,
            textRequestValidator = textRequestValidator,
        )

    fun handle(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse =
        try {
            handshakeValidator.validate(request)
            executeWithBridgeAdmission(request, metrics, deduper) { handleValidatedSafely(request) }
        } catch (e: Exception) {
            bridgeRequestFailureResponse(request, e, metrics, logError)
        }

    private fun handleValidatedSafely(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse =
        try {
            handleValidated(request)
        } catch (e: Exception) {
            bridgeRequestFailureResponse(request, e, metrics, logError)
        }

    private fun handleValidated(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse =
        when (val action = request.action) {
            ImageBridgeProtocol.ACTION_SEND_IMAGE -> handleSendImage(request)
            ImageBridgeProtocol.ACTION_SEND_TEXT -> handleSendText(request, markdown = false)
            ImageBridgeProtocol.ACTION_SEND_MARKDOWN -> handleSendText(request, markdown = true)
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

    private fun handleSendImage(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse = imageActionHandler.handle(request, healthProvider())

    private fun handleSendText(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        markdown: Boolean,
    ): ImageBridgeProtocol.ImageBridgeResponse = textActionHandler.handle(request, healthProvider(), markdown)

    private fun handleInspectChatRoom(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val inspector = checkNotNull(chatRoomInspector) { "chatroom inspection unavailable" }
        return ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK, inspectionJson = inspector(roomId))
    }

    private fun handleOpenChatRoom(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val opener = checkNotNull(chatRoomOpener) { "chatroom opener unavailable" }
        opener(roomId)
        return ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK, requestId = request.requestId)
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
}
