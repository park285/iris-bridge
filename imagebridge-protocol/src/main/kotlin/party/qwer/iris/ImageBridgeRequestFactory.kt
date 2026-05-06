package party.qwer.iris

interface ImageBridgeRequestFactory {
    fun buildSendImageRequest(
        roomId: Long,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ImageBridgeProtocol.ACTION_SEND_IMAGE,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            roomId = roomId,
            imagePaths = imagePaths,
            threadId = threadId,
            threadScope = threadScope,
            requestId = requestId,
            token = token,
        )

    fun buildSendTextRequest(
        roomId: Long,
        message: String,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String? = null,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        textRequest(
            action = ImageBridgeProtocol.ACTION_SEND_TEXT,
            roomId = roomId,
            message = message,
            threadId = threadId,
            threadScope = threadScope,
            mentionsJson = mentionsJson,
            requestId = requestId,
            token = token,
            markdown = false,
        )

    fun buildSendMarkdownRequest(
        roomId: Long,
        message: String,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String? = null,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        textRequest(
            action = ImageBridgeProtocol.ACTION_SEND_MARKDOWN,
            roomId = roomId,
            message = message,
            threadId = threadId,
            threadScope = threadScope,
            mentionsJson = mentionsJson,
            requestId = requestId,
            token = token,
            markdown = true,
        )

    fun buildHealthRequest(token: String? = null): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ImageBridgeProtocol.ACTION_HEALTH,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            token = token,
        )

    fun buildInspectChatRoomRequest(
        roomId: Long,
        token: String? = null,
    ): ImageBridgeRequest = roomRequest(ImageBridgeProtocol.ACTION_INSPECT_CHATROOM, roomId, token)

    fun buildOpenChatRoomRequest(
        roomId: Long,
        token: String? = null,
    ): ImageBridgeRequest = roomRequest(ImageBridgeProtocol.ACTION_OPEN_CHATROOM, roomId, token)

    fun buildSnapshotChatRoomMembersRequest(
        roomId: Long,
        memberIds: List<Long> = emptyList(),
        memberHints: List<ChatRoomMemberHint> = emptyList(),
        preferredMemberPlan: ChatRoomMemberExtractionPlan? = null,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            roomId = roomId,
            token = token,
            memberIds = memberIds,
            memberHints = memberHints,
            preferredMemberPlan = preferredMemberPlan,
        )
}

private fun textRequest(
    action: String,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    mentionsJson: String?,
    requestId: String?,
    token: String?,
    markdown: Boolean,
): ImageBridgeRequest =
    ImageBridgeRequest(
        action = action,
        protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
        roomId = roomId,
        message = message,
        markdown = markdown,
        mentionsJson = mentionsJson,
        threadId = threadId,
        threadScope = threadScope,
        requestId = requestId,
        token = token,
    )

private fun roomRequest(
    action: String,
    roomId: Long,
    token: String?,
): ImageBridgeRequest =
    ImageBridgeRequest(
        action = action,
        protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
        roomId = roomId,
        token = token,
    )
