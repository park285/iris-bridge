@file:Suppress("FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KakaoUserDatabaseReaderTest {
    @Test
    fun `readNicknames returns nickname for known user via reflection`() {
        val access =
            buildFakeUserDbAccess { userId ->
                when (userId) {
                    100L -> FakeUserModel(100L, "Alice")
                    200L -> FakeUserModel(200L, "Bob")
                    else -> null
                }
            }
        val reader = KakaoUserDatabaseReader(access)

        val result = reader.readNicknames(listOf(100L, 200L, 300L))

        assertEquals("Alice", result[100L])
        assertEquals("Bob", result[200L])
        assertTrue(300L !in result)
    }

    @Test
    fun `readNicknames returns empty map for empty input`() {
        val access = buildFakeUserDbAccess { null }
        val reader = KakaoUserDatabaseReader(access)

        assertTrue(reader.readNicknames(emptyList()).isEmpty())
    }

    @Test
    fun `readNicknames skips blank nicknames`() {
        val access =
            buildFakeUserDbAccess { userId ->
                if (userId == 1L) FakeUserModel(1L, "  ") else null
            }
        val reader = KakaoUserDatabaseReader(access)

        assertTrue(reader.readNicknames(listOf(1L)).isEmpty())
    }

    @Test
    fun `readNicknames skips null user entries`() {
        val access = buildFakeUserDbAccess { null }
        val reader = KakaoUserDatabaseReader(access)

        assertTrue(reader.readNicknames(listOf(1L, 2L, 3L)).isEmpty())
    }

    @Test
    fun `readNicknames deduplicates input user ids`() {
        val callCount = mutableMapOf<Long, Int>()
        val access =
            buildFakeUserDbAccess { userId ->
                callCount[userId] = (callCount[userId] ?: 0) + 1
                FakeUserModel(userId, "Name$userId")
            }
        val reader = KakaoUserDatabaseReader(access)

        reader.readNicknames(listOf(1L, 1L, 2L))

        assertEquals(1, callCount[1L])
        assertEquals(1, callCount[2L])
    }
}
