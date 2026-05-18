@file:Suppress("ClassName", "FunctionName")

package com.kakao.talk.model.kakaolink

object FakeKakaoLinkSpecRecorder {
    val calls: MutableList<String> = mutableListOf()
    var existingChatIdResult: Any? = Unit
    var receiverResult: Any? = Unit

    fun clear() {
        calls.clear()
        existingChatIdResult = Unit
        receiverResult = Unit
    }
}

class b {
    companion object {
        @JvmStatic
        fun b(query: String): FakeKakaoLinkSpec = FakeKakaoLinkSpec(query)
    }
}

class FakeKakaoLinkSpec(
    @Suppress("unused") private val query: String,
) {
    fun a(
        @Suppress("unused") listener: Any?,
        receiver: Long,
    ): Any? {
        FakeKakaoLinkSpecRecorder.calls += "a:$receiver"
        return FakeKakaoLinkSpecRecorder.receiverResult
    }

    fun b(
        roomId: Long,
        @Suppress("unused") ids: LongArray?,
        @Suppress("unused") listener: Any?,
    ): Any? {
        FakeKakaoLinkSpecRecorder.calls += "b:$roomId"
        return FakeKakaoLinkSpecRecorder.receiverResult
    }

    fun c(roomId: Long): Any? {
        FakeKakaoLinkSpecRecorder.calls += "c:$roomId"
        return FakeKakaoLinkSpecRecorder.existingChatIdResult
    }
}
