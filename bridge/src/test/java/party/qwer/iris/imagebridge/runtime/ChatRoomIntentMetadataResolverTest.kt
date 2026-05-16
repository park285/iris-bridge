@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatRoomIntentMetadataResolverTest {
    @Test
    fun `resolves Kakao chatroom type value from room`() {
        val resolver = ChatRoomIntentMetadataResolver { FakeRoom(FakeChatRoomType.OpenMulti) }

        assertEquals("OM", resolver.resolveChatRoomType(123L))
    }

    @Test
    fun `falls back to enum name when value accessor is unavailable`() {
        val resolver = ChatRoomIntentMetadataResolver { FakeRoomWithoutValue(FallbackType.NormalMulti) }

        assertEquals("NormalMulti", resolver.resolveChatRoomType(123L))
    }

    @Test
    fun `returns null when room cannot be resolved`() {
        val resolver = ChatRoomIntentMetadataResolver { null }

        assertEquals(null, resolver.resolveChatRoomType(123L))
    }

    private class FakeRoom(
        private val type: FakeChatRoomType,
    ) {
        @Suppress("unused")
        fun y1(): FakeChatRoomType = type
    }

    private enum class FakeChatRoomType(
        private val value: String,
    ) {
        OpenMulti("OM"),
        ;

        @Suppress("unused")
        fun getValue(): String = value
    }

    private class FakeRoomWithoutValue(
        private val type: FallbackType,
    ) {
        @Suppress("unused")
        fun y1(): FallbackType = type
    }

    private enum class FallbackType {
        NormalMulti,
    }
}
