package party.qwer.iris.imagebridge.runtime.send

internal interface KakaoSendInvoker {
    fun sendSingle(
        chatRoom: Any,
        imagePath: String,
        threadId: Long?,
        threadScope: Int?,
    ) {
        sendSingle(chatRoom, imagePath, null, threadId, threadScope)
    }

    fun sendSingle(
        chatRoom: Any,
        imagePath: String,
        contentType: String?,
        threadId: Long?,
        threadScope: Int?,
    )

    fun sendMultiple(
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
    ) {
        sendMultiple(chatRoom, imagePaths, emptyList(), threadId, threadScope)
    }

    fun sendMultiple(
        chatRoom: Any,
        imagePaths: List<String>,
        contentTypes: List<String>,
        threadId: Long?,
        threadScope: Int?,
    )

    fun sendThreaded(
        roomId: Long,
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long,
        threadScope: Int,
    ) {
        sendThreaded(roomId, chatRoom, imagePaths, emptyList(), threadId, threadScope)
    }

    fun sendThreaded(
        roomId: Long,
        chatRoom: Any,
        imagePaths: List<String>,
        contentTypes: List<String>,
        threadId: Long,
        threadScope: Int,
    )
}
