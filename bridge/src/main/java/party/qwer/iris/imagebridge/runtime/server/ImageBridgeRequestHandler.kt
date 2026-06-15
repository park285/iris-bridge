package party.qwer.iris.imagebridge.runtime.server

import android.util.Log
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor
import party.qwer.iris.imagebridge.runtime.send.TextSendRequest

internal class ImageBridgeRequestHandler(
    private val imageSender: (ImageSendRequest) -> Unit,
    private val textSender: ((TextSendRequest) -> Unit)? = null,
    private val healthProvider: () -> ImageBridgeHealthSnapshot,
    private val chatRoomInspector: ((Long) -> String)? = null,
    private val chatRoomOpener: ((Long) -> Unit)? = null,
    private val chatRoomReadMarker: ((Long) -> Unit)? = null,
    private val chatRoomMemberSnapshotProvider: ((Long, List<ImageBridgeProtocol.ChatRoomMemberHint>, ImageBridgeProtocol.ChatRoomMemberExtractionPlan?) -> ImageBridgeProtocol.ChatRoomMembersSnapshot)? = null,
    private val memberProfileFetcher: ((Long, List<Long>) -> Map<Long, UpstreamMemberProfile>)? = null,
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
    private val chatRoomActionHandler =
        BridgeChatRoomActionHandler(
            inspector = chatRoomInspector,
            opener = chatRoomOpener,
            readMarker = chatRoomReadMarker,
            memberSnapshotProvider = chatRoomMemberSnapshotProvider,
        )
    private val memberProfileActionHandler = BridgeMemberProfileActionHandler(memberProfileFetcher)

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
            ImageBridgeProtocol.ACTION_INSPECT_CHATROOM -> chatRoomActionHandler.handleInspect(request)
            ImageBridgeProtocol.ACTION_OPEN_CHATROOM -> chatRoomActionHandler.handleOpen(request)
            ImageBridgeProtocol.ACTION_MARK_CHATROOM_READ -> chatRoomActionHandler.handleMarkRead(request)
            ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS -> chatRoomActionHandler.handleSnapshotMembers(request)
            ImageBridgeProtocol.ACTION_FETCH_MEMBER_PROFILES -> memberProfileActionHandler.handle(request)
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
}
