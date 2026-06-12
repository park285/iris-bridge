package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.server.ValidatedBridgeImagePath

internal data class ImageSendRequest(
    val roomId: Long,
    val imagePaths: List<ValidatedBridgeImagePath>,
    val contentTypes: List<String>,
    val threadId: Long?,
    val threadScope: Int?,
    val requestId: String?,
)
