package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import android.util.Log

internal fun logDiscoveryComplete(
    chatMediaSender: Class<*>,
    messageType: Class<*>,
    writeType: Class<*>,
    listener: Class<*>,
    chatRoomManager: Class<*>,
    chatRoom: Class<*>,
) {
    Log.i(
        KAKAO_CLASS_REGISTRY_TAG,
        "KakaoClassRegistry.discover complete — " +
            "ChatMediaSender=${chatMediaSender.name}, " +
            "MessageType=${messageType.name}, " +
            "WriteType=${writeType.name}, " +
            "Listener=${listener.name}, " +
            "ChatRoomManager=${chatRoomManager.name}, " +
            "ChatRoom=${chatRoom.name}",
    )
}
