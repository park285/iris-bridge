@file:Suppress("ClassName", "FunctionName")

package com.kakao.talk.model.kakaolink

object FakeKakaoLinkSpecRecorder {
    val calls: MutableList<String> = mutableListOf()

    fun clear() {
        calls.clear()
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
    fun b(
        roomId: Long,
        @Suppress("unused") ids: LongArray?,
        @Suppress("unused") listener: Any?,
    ) {
        FakeKakaoLinkSpecRecorder.calls += "b:$roomId"
    }

    fun c(roomId: Long) {
        FakeKakaoLinkSpecRecorder.calls += "c:$roomId"
    }
}
