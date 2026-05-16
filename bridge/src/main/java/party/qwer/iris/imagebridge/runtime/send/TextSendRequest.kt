package party.qwer.iris.imagebridge.runtime.send

internal data class TextSendRequest(
    val roomId: Long,
    val message: String,
    val markdown: Boolean,
    val threadId: Long?,
    val threadScope: Int?,
    val mentionsJson: String?,
    val attachmentJson: String?,
    val requestId: String?,
)
