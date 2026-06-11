package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.NoopBridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.KakaoMemberProfileFetcher
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.discoverKakaoMemberFetchAccess
import party.qwer.iris.imagebridge.runtime.kakao.userdb.KakaoCachedMemberProfileFetcher
import party.qwer.iris.imagebridge.runtime.kakao.userdb.KakaoUserDatabaseReader
import party.qwer.iris.imagebridge.runtime.kakao.userdb.discoverKakaoUserDatabaseAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.room.ChatRoomMemberExtractor
import party.qwer.iris.imagebridge.runtime.room.ChatRoomMemberSnapshotEnricher
import party.qwer.iris.imagebridge.runtime.room.ChatRoomOpener
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver
import party.qwer.iris.imagebridge.runtime.room.defaultChatRoomIntrospector
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
    leveragePendingContexts: ReplyLeveragePendingContextStore?,
    leverageCommitPendingContexts: ReplyLeveragePendingContextStore?,
    hookInstaller: BridgeHookInstaller = NoopBridgeHookInstaller,
    healthProvider: () -> ImageBridgeHealthSnapshot,
    bridgeMetrics: BridgeMetrics,
    bridgeCore: BridgeCoreRuntime,
): BridgeRequestHandlerComponents {
    val imageSender = registry?.let { KakaoImageSender(it, hookInstaller) }
    val textSender = registry?.let { KakaoTextSender(context, it, mentionPendingContexts, leveragePendingContexts, leverageCommitPendingContexts) }
    val chatRoomResolver = registry?.let { ChatRoomResolver(it) }
    val chatRoomOpener = chatRoomOpener(context, registry, chatRoomResolver)
    val memberProfileFetcher: MemberProfileUpstream? =
        discoverKakaoMemberFetchAccess(context.classLoader)?.let { fetchAccess ->
            discoverKakaoUserDatabaseAccess(context.classLoader)?.let { userDbAccess ->
                KakaoCachedMemberProfileFetcher(
                    KakaoMemberProfileFetcher(fetchAccess),
                    KakaoUserDatabaseReader(userDbAccess),
                )
            }
        }
    val memberExtractor = ChatRoomMemberExtractor()
    val memberSnapshotEnricher =
        ChatRoomMemberSnapshotEnricher(
            upstreamFetcher = memberProfileFetcher,
        )
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
                    val snapshot = memberExtractor.snapshot(roomId, room, expectedMemberHints, preferredPlan)
                    memberSnapshotEnricher.enrich(snapshot, expectedMemberHints)
                },
                memberProfileFetcher = { roomId, userIds ->
                    requireNotNull(memberProfileFetcher) { "Kakao member profile fetcher unavailable" }
                        .fetchMemberProfiles(roomId, userIds)
                },
                metrics = bridgeMetrics,
                textRequestValidator = BridgeTextRequestValidator(bridgeCore),
                pathValidator = BridgeImagePathValidator(bridgeCore = bridgeCore),
            ),
        initialSpecStatus = initialSpecStatus,
        textSendCapability = textSender?.capability(),
    )
}

private fun chatRoomOpener(
    context: Context,
    registry: KakaoClassRegistry?,
    chatRoomResolver: ChatRoomResolver?,
): ChatRoomOpener {
    val metadataResolver =
        ChatRoomIntentMetadataResolver { roomId ->
            chatRoomResolver?.resolve(roomId)
        }
    return ChatRoomOpener(
        context,
        kakaoPackage = registry?.target?.packageName ?: party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTarget.OFFICIAL_PACKAGE,
        chatRoomTypeResolver = metadataResolver::resolveChatRoomType,
    )
}

private fun inspectChatRoom(
    chatRoomResolver: ChatRoomResolver?,
    registryError: String?,
    roomId: Long,
): String {
    val room = resolveChatRoom(chatRoomResolver, registryError, roomId)
    return defaultChatRoomIntrospector.scanJson(room, maxDepth = 2)
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
