@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.room.ChatRoomResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChatRoomResolverTest {
    @Test
    fun `resolve uses database path with registry`() {
        FakeChatRuntime.reset()
        val registry = buildFakeRegistry()
        val resolver = ChatRoomResolver(registry = registry)

        val first = resolver.resolve(101L)
        val second = resolver.resolve(102L)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(listOf(101L, 102L), FakeChatRuntime.resolvedRoomIds)
    }

    @Test
    fun `resolve prefers exact legacy companion resolver name`() {
        FakeChatRuntime.reset()
        LegacyNameSensitiveRecorder.calls.clear()
        val resolver = ChatRoomResolver(registry = buildLegacyNameSensitiveRegistry())

        resolver.resolve(777L)

        assertEquals(listOf("c"), LegacyNameSensitiveRecorder.calls)
    }

    @Test
    fun `resolveFresh prefers manager path over database path`() {
        FakeChatRuntime.reset()
        val resolver = ChatRoomResolver(registry = buildFakeRegistry())

        val room = resolver.resolveFresh(202L)

        assertNotNull(room)
        assertEquals(listOf(202L), FakeChatRuntime.managerRoomIds)
        assertEquals(emptyList(), FakeChatRuntime.databaseRoomIds)
    }
}
