package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntrospector
import party.qwer.iris.imagebridge.runtime.room.ChatRoomMemberExtractor
import party.qwer.iris.imagebridge.runtime.room.ChatRoomOpener
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver
import party.qwer.iris.imagebridge.runtime.send.KakaoImageSender
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSendCapability
import party.qwer.iris.imagebridge.runtime.send.KakaoTextSender

internal data class BridgeRequestHandlerComponents(
    val requestHandler: ImageBridgeRequestHandler,
    val initialSpecStatus: BridgeSpecStatus,
    val textSendCapability: KakaoTextSendCapability?,
)

internal fun buildBridgeRequestHandlerComponents(
    context: Context,
    registry: KakaoClassRegistry?,
    registryError: String?,
    mentionPendingContexts: ReplyMentionPendingContextStore?,
    healthProvider: () -> ImageBridgeHealthSnapshot,
    bridgeMetrics: BridgeMetrics,
): BridgeRequestHandlerComponents {
    val imageSender = registry?.let { KakaoImageSender(it) }
    val textSender = registry?.let { KakaoTextSender(context, it, mentionPendingContexts) }
    val chatRoomResolver = registry?.let { ChatRoomResolver(it) }
    val chatRoomOpener = chatRoomOpener(context, chatRoomResolver)
    val memberExtractor = ChatRoomMemberExtractor()
    val initialSpecStatus = BridgeHookSpecVerifier(registry, registryError).verify()
    return BridgeRequestHandlerComponents(
        requestHandler =
            ImageBridgeRequestHandler(
                imageSender = { request -> requireNotNull(imageSender) { "KakaoClassRegistry not available: ${registryError ?: "unknown error"}" }.send(request) },
                textSender = { request -> requireNotNull(textSender) { "Kakao text sender not available: ${registryError ?: "unknown error"}" }.send(request) },
                healthProvider = healthProvider,
                chatRoomInspector = { roomId -> inspectChatRoom(chatRoomResolver, registryError, roomId) },
                chatRoomOpener = chatRoomOpener::open,
                chatRoomMemberSnapshotProvider = { roomId, expectedMemberHints, preferredPlan ->
                    val room = resolveFreshChatRoom(chatRoomResolver, registryError, roomId)
                    memberExtractor.snapshot(roomId, room, expectedMemberHints, preferredPlan)
                },
                metrics = bridgeMetrics,
            ),
        initialSpecStatus = initialSpecStatus,
        textSendCapability = textSender?.capability(),
    )
}

private fun chatRoomOpener(
    context: Context,
    chatRoomResolver: ChatRoomResolver?,
): ChatRoomOpener {
    val metadataResolver =
        ChatRoomIntentMetadataResolver { roomId ->
            chatRoomResolver?.resolve(roomId)
        }
    return ChatRoomOpener(context, chatRoomTypeResolver = metadataResolver::resolveChatRoomType)
}

private fun inspectChatRoom(
    chatRoomResolver: ChatRoomResolver?,
    registryError: String?,
    roomId: Long,
): String {
    val room = resolveChatRoom(chatRoomResolver, registryError, roomId)
    return ChatRoomIntrospector.scanJson(room, maxDepth = 2)
}

private fun resolveChatRoom(
    chatRoomResolver: ChatRoomResolver?,
    registryError: String?,
    roomId: Long,
): Any {
    val resolver = chatRoomResolver ?: error("chatroom resolver unavailable: ${registryError ?: "unknown error"}")
    return resolver.resolve(roomId) ?: error("chatroom not found: $roomId")
}

private fun resolveFreshChatRoom(
    chatRoomResolver: ChatRoomResolver?,
    registryError: String?,
    roomId: Long,
): Any {
    val resolver = chatRoomResolver ?: error("chatroom resolver unavailable: ${registryError ?: "unknown error"}")
    return resolver.resolveFresh(roomId) ?: error("chatroom not found: $roomId")
}
