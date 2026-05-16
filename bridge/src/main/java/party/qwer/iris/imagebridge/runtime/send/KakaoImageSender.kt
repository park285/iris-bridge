package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver

internal class KakaoImageSender(
    private val chatRoomResolver: (Long) -> Any?,
    private val sendInvocationFactory: KakaoSendInvoker,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.d(tag, message) },
) {
    companion object {
        private const val TAG = "IrisBridge"
    }

    constructor(
        registry: KakaoClassRegistry,
        hookInstaller: BridgeHookInstaller,
    ) : this(
        chatRoomResolver = ChatRoomResolver(registry)::resolve,
        sendInvocationFactory = KakaoSendInvocationFactory(registry, hookInstaller),
    )

    fun send(request: ImageSendRequest) {
        send(
            roomId = request.roomId,
            imagePaths = request.imagePaths.map { it.revalidate() },
            threadId = request.threadId,
            threadScope = request.threadScope,
            requestId = request.requestId,
        )
    }

    fun send(
        roomId: Long,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
        requestId: String?,
    ) {
        require(imagePaths.isNotEmpty()) { "no image paths" }
        logInfo(TAG, "send start room=$roomId images=${imagePaths.size} threadId=$threadId scope=$threadScope requestId=$requestId")

        val chatRoom = chatRoomResolver(roomId) ?: error("chat room not found: $roomId")
        logInfo(TAG, "resolved chatRoom class=${chatRoom.javaClass.name} room=$roomId")
        if (threadId != null && threadScope != null && threadScope >= 2) {
            sendInvocationFactory.sendThreaded(roomId, chatRoom, imagePaths, threadId, threadScope)
        } else if (imagePaths.size == 1) {
            sendInvocationFactory.sendSingle(chatRoom, imagePaths.first(), threadId, threadScope)
        } else {
            sendInvocationFactory.sendMultiple(chatRoom, imagePaths, threadId, threadScope)
        }
        logInfo(TAG, "send completed room=$roomId requestId=$requestId")
    }
}
