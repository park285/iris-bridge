package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor
import party.qwer.iris.imagebridge.runtime.send.TextSendRequest

private const val MAX_TEXT_MESSAGE_LENGTH = 100_000
private const val MAX_ATTACHMENT_JSON_LENGTH = 100_000

internal class BridgeTextActionHandler(
    private val textSender: ((TextSendRequest) -> Unit)?,
    private val serialExecutor: RoomThreadSerialExecutor,
    private val metrics: BridgeMetrics,
) {
    fun handle(
        request: ImageBridgeProtocol.ImageBridgeRequest,
        health: ImageBridgeHealthSnapshot,
        markdown: Boolean,
    ): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val message = requireNotNull(request.message) { "message missing" }
        val attachmentJson = validateAttachmentJson(request.attachmentJson, markdown, request.mentionsJson)
        require(message.isNotBlank()) { "message is blank" }
        require(message.length <= MAX_TEXT_MESSAGE_LENGTH) { "message is too long: ${message.length}" }
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

private fun validateAttachmentJson(
    raw: String?,
    markdown: Boolean,
    mentionsJson: String?,
): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    require(!markdown) { "attachmentJson is only supported for send_text" }
    require(mentionsJson.isNullOrBlank()) { "attachmentJson cannot be combined with mentionsJson" }
    require(value.length <= MAX_ATTACHMENT_JSON_LENGTH) { "attachmentJson is too long: ${value.length}" }
    runCatching { JSONObject(value) }.getOrElse {
        throw IllegalArgumentException("attachmentJson must be a JSON object")
    }
    return value
}
