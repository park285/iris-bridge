package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.discovery.sendBlockReason
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor

internal class BridgeImageActionHandler(
    private val imageSender: (ImageSendRequest) -> Unit,
    private val serialExecutor: RoomThreadSerialExecutor,
    private val pathValidator: BridgeImagePathValidator,
    private val metrics: BridgeMetrics,
    private val leaseVerifier: BridgeImageLeaseVerifier = BridgeImageLeaseVerifier(),
) {
    fun handle(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        health: ImageBridgeHealthSnapshot,
    ): ImageBridgeProtocol.ImageBridgeResponse {
        check(health.specStatus.ready) { "bridge spec not ready" }
        val validatedPaths = pathValidator.validate(request.imagePaths)
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val requestId = checkNotNull(request.requestId) { "requestId missing" }
        leaseVerifier.verify(roomId, requestId, request.imageLeases, validatedPaths)
        val imageRequest =
            ImageSendRequest(
                roomId = roomId,
                imagePaths = validatedPaths,
                threadId = request.threadId,
                threadScope = request.threadScope,
                requestId = requestId,
            )
        health.discoverySnapshot
            .sendBlockReason(
                imageCount = imageRequest.imagePaths.size,
                threadId = imageRequest.threadId,
                threadScope = imageRequest.threadScope,
            )?.let { reason -> error(reason) }
        val startedAtEpochMs = System.currentTimeMillis()
        metrics.recordSendStart(imageRequest.requestId, startedAtEpochMs)
        serialExecutor.executeSynchronously(imageRequest.roomId, imageRequest.threadId) {
            imageSender(imageRequest)
        }
        val completedAtEpochMs = System.currentTimeMillis()
        metrics.recordSendSuccess(completedAtEpochMs, completedAtEpochMs - startedAtEpochMs)
        return ImageBridgeProtocol.buildSuccessResponse(requestId)
    }
}
