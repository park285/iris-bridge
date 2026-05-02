package party.qwer.iris.imagebridge.runtime.send

internal data class ImageSendRequest(
    val roomId: Long,
    val imagePaths: List<String>,
    val threadId: Long?,
    val threadScope: Int?,
    val requestId: String?,
)
