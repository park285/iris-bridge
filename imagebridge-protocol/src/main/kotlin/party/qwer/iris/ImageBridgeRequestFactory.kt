package party.qwer.iris

interface ImageBridgeRequestFactory {
    fun buildSendImageRequest(
        roomId: Long,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
        requestId: String? = null,
        token: String? = null,
        imageLeases: List<SignedImageLease> = emptyList(),
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ImageBridgeProtocol.ACTION_SEND_IMAGE,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            roomId = roomId,
            imagePaths = imagePaths,
            imageLeases = imageLeases,
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
        attachmentJson: String? = null,
    ): ImageBridgeRequest =
        textRequest(
            action = ImageBridgeProtocol.ACTION_SEND_TEXT,
            roomId = roomId,
            message = message,
            threadId = threadId,
            threadScope = threadScope,
            mentionsJson = mentionsJson,
            attachmentJson = attachmentJson,
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
            attachmentJson = null,
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
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest = roomRequest(ImageBridgeProtocol.ACTION_OPEN_CHATROOM, roomId, token, requestId)

    fun buildMarkChatRoomReadRequest(
        roomId: Long,
        requestId: String? = null,
        token: String? = null,
    ): ImageBridgeRequest = roomRequest(ImageBridgeProtocol.ACTION_MARK_CHATROOM_READ, roomId, token, requestId)

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

    fun buildFetchMemberProfilesRequest(
        roomId: Long,
        memberIds: List<Long>,
        token: String? = null,
    ): ImageBridgeRequest =
        ImageBridgeRequest(
            action = ImageBridgeProtocol.ACTION_FETCH_MEMBER_PROFILES,
            protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
            roomId = roomId,
            token = token,
            memberIds = memberIds,
        )
}

private fun textRequest(
    action: String,
    roomId: Long,
    message: String,
    threadId: Long?,
    threadScope: Int?,
    mentionsJson: String?,
    attachmentJson: String?,
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
        attachmentJson = attachmentJson,
        threadId = threadId,
        threadScope = threadScope,
        requestId = requestId,
        token = token,
    )

private fun roomRequest(
    action: String,
    roomId: Long,
    token: String?,
    requestId: String? = null,
): ImageBridgeRequest =
    ImageBridgeRequest(
        action = action,
        protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
        roomId = roomId,
        token = token,
        requestId = requestId,
    )
