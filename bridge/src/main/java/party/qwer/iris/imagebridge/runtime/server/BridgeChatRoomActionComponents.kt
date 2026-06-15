package party.qwer.iris.imagebridge.runtime.server

import android.content.Context
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTarget
import party.qwer.iris.imagebridge.runtime.notification.KakaoNotificationActionStarter
import party.qwer.iris.imagebridge.runtime.notification.isKakaoNotificationMarkReadAvailable
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.room.ChatRoomOpener
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver

internal fun buildNotificationActionStarter(
    context: Context,
    registry: KakaoClassRegistry?,
): KakaoNotificationActionStarter =
    KakaoNotificationActionStarter(
        context,
        kakaoPackage = kakaoPackage(registry),
    )

internal fun isNotificationActionSupported(
    context: Context,
    registry: KakaoClassRegistry?,
): Boolean = isKakaoNotificationMarkReadAvailable(context, kakaoPackage(registry))

internal fun buildChatRoomOpener(
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
        kakaoPackage = kakaoPackage(registry),
        chatRoomTypeResolver = metadataResolver::resolveChatRoomType,
    )
}

private fun kakaoPackage(registry: KakaoClassRegistry?): String = registry?.target?.packageName ?: KakaoTalkTarget.OFFICIAL_PACKAGE
