package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol

internal class BridgeChatRoomActionHandler(
    private val inspector: ((Long) -> String)?,
    private val opener: ((Long) -> Unit)?,
    private val readMarker: ((Long) -> Unit)?,
    private val memberSnapshotProvider: ((Long, List<ImageBridgeProtocol.ChatRoomMemberHint>, ImageBridgeProtocol.ChatRoomMemberExtractionPlan?) -> ImageBridgeProtocol.ChatRoomMembersSnapshot)?,
) {
    fun handleInspect(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = roomId(request)
        val inspect = checkNotNull(inspector) { "chatroom inspection unavailable" }
        return ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK, inspectionJson = inspect(roomId))
    }

    fun handleOpen(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse = handleUnitAction(request, opener, "chatroom opener unavailable")

    fun handleMarkRead(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse = handleUnitAction(request, readMarker, "chatroom read marker unavailable")

    fun handleSnapshotMembers(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = roomId(request)
        val provider = checkNotNull(memberSnapshotProvider) { "chatroom member snapshot unavailable" }
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

    private fun handleUnitAction(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        handler: ((Long) -> Unit)?,
        unavailableMessage: String,
    ): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = roomId(request)
        checkNotNull(handler) { unavailableMessage }(roomId)
        return ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK, requestId = request.requestId)
    }

    private fun roomId(request: ImageBridgeProtocol.ImageBridgeRequest): Long = checkNotNull(request.roomId) { "roomId missing" }
}
