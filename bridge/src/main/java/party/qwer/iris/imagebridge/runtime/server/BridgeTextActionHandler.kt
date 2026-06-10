package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor
import party.qwer.iris.imagebridge.runtime.send.TextSendRequest

internal class BridgeTextActionHandler(
    private val textSender: ((TextSendRequest) -> Unit)?,
    private val serialExecutor: RoomThreadSerialExecutor,
    private val metrics: BridgeMetrics,
    private val textRequestValidator: BridgeTextRequestValidator = BridgeTextRequestValidator(),
) {
    fun handle(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        health: ImageBridgeHealthSnapshot,
        markdown: Boolean,
    ): ImageBridgeProtocol.ImageBridgeResponse {
        val attachmentJson = textRequestValidator.validate(request, markdown)
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val message = requireNotNull(request.message) { "message missing" }
        check(health.specStatus.ready) { "bridge spec not ready" }
        val capability = if (markdown) health.capabilities.sendMarkdown else health.capabilities.sendText
        check(capability.ready) { capability.reason ?: "text sender unavailable" }
        val sender = checkNotNull(textSender) { "text sender unavailable" }
        val textRequest =
            TextSendRequest(
                roomId = roomId,
                message = message,
                markdown = markdown,
                threadId = request.threadId,
                threadScope = request.threadScope,
                mentionsJson = request.mentionsJson,
                attachmentJson = attachmentJson,
                requestId = request.requestId,
            )
        val startedAtEpochMs = System.currentTimeMillis()
        metrics.recordSendStart(textRequest.requestId, startedAtEpochMs)
        serialExecutor.executeSynchronously(textRequest.roomId, textRequest.threadId) {
            sender(textRequest)
        }
        val completedAtEpochMs = System.currentTimeMillis()
        metrics.recordSendSuccess(completedAtEpochMs, completedAtEpochMs - startedAtEpochMs)
        return ImageBridgeProtocol.ImageBridgeResponse(
            status = ImageBridgeProtocol.STATUS_SENT,
            requestId = request.requestId,
        )
    }
}
