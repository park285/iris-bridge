package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver

internal class KakaoTextSender(
    private val chatRoomResolver: (Long) -> Any?,
    private val invoker: KakaoTextSendInvoker,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
) {
    constructor(registry: KakaoClassRegistry) : this(
        chatRoomResolver = ChatRoomResolver(registry)::resolve,
        invoker = KakaoTextSendInvocationFactory(registry),
    )

    fun capability(): KakaoTextSendCapability = invoker.capability()

    fun send(request: TextSendRequest) {
        logInfo(
            TAG,
            "text send start room=${request.roomId} markdown=${request.markdown} " +
                "threadId=${request.threadId} scope=${request.threadScope} requestId=${request.requestId} length=${request.message.length}",
        )
        val chatRoom = chatRoomResolver(request.roomId) ?: error("chat room not found: ${request.roomId}")
        invoker.send(
            roomId = request.roomId,
            chatRoom = chatRoom,
            message = request.message,
            markdown = request.markdown,
            threadId = request.threadId,
            threadScope = request.threadScope,
            mentionsJson = request.mentionsJson,
            requestId = request.requestId,
        )
        logInfo(TAG, "text send completed room=${request.roomId} requestId=${request.requestId}")
    }

    private companion object {
        private const val TAG = "IrisBridge"
    }
}
