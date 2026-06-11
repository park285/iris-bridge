@file:Suppress("FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
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
    fun `readNicknames accepts Kakao 26_4_2 getNickname accessor`() {
        val access =
            buildFakeUserDbAccess {
                FakeGetNicknameUserModel("Alice")
            }
        val reader = KakaoUserDatabaseReader(access)

        val result = reader.readNicknames(listOf(100L))

        assertEquals("Alice", result[100L])
    }

    @Test
    fun `readNicknames accepts Kakao 26_4_2 obfuscated nickname accessor r`() {
        val access =
            buildFakeUserDbAccess {
                FakeObfuscatedNicknameUserModel("Bob")
            }
        val reader = KakaoUserDatabaseReader(access)

        val result = reader.readNicknames(listOf(200L))

        assertEquals("Bob", result[200L])
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

    @Test
    fun `readNicknames uses continuation proxy when access method requires foreign continuation type`() {
        val instance = FakeForeignContinuationUserDataSource()
        val method =
            FakeForeignContinuationUserDataSource::class.java.methods.first { method ->
                method.name == "getUserByIdV2"
            }
        val reader =
            KakaoUserDatabaseReader(
                KakaoUserDatabaseAccess(
                    singleton = instance,
                    getUserByIdV2Method = method,
                ),
            )

        val result = reader.readNicknames(listOf(77L))

        assertEquals("Proxy77", result[77L])
    }

    @Test
    fun `readNicknames unwraps boxed Kakao Result from continuation proxy`() {
        val instance = FakeBoxedResultUserDataSource()
        val method =
            FakeBoxedResultUserDataSource::class.java.methods.first { method ->
                method.name == "getUserByIdV2"
            }
        val reader =
            KakaoUserDatabaseReader(
                KakaoUserDatabaseAccess(
                    singleton = instance,
                    getUserByIdV2Method = method,
                ),
            )

        val result = reader.readNicknames(listOf(88L))

        assertEquals("Boxed88", result[88L])
    }

    @Test
    fun `readNicknames unwraps nested boxed Kakao Result from continuation proxy`() {
        val instance = FakeNestedBoxedResultUserDataSource()
        val method =
            FakeNestedBoxedResultUserDataSource::class.java.methods.first { method ->
                method.name == "getUserByIdV2"
            }
        val reader =
            KakaoUserDatabaseReader(
                KakaoUserDatabaseAccess(
                    singleton = instance,
                    getUserByIdV2Method = method,
                ),
            )

        val result = reader.readNicknames(listOf(89L))

        assertEquals("Nested89", result[89L])
    }

    @Test
    fun `readNicknames unwraps nested boxed Kakao Result when continuation type is shared`() {
        val instance = FakeSharedContinuationNestedResultUserDataSource()
        val method =
            FakeSharedContinuationNestedResultUserDataSource::class.java.methods.first { method ->
                method.name == "getUserByIdV2"
            }
        val reader =
            KakaoUserDatabaseReader(
                KakaoUserDatabaseAccess(
                    singleton = instance,
                    getUserByIdV2Method = method,
                ),
            )

        val result = reader.readNicknames(listOf(90L))

        assertEquals("Shared90", result[90L])
    }
}

private interface FakeForeignContinuation {
    fun getContext(): Any?

    fun resumeWith(result: Any?)
}

private class FakeForeignContinuationUserDataSource {
    fun getUserByIdV2(
        userId: Long,
        continuation: FakeForeignContinuation,
    ): Any {
        continuation.resumeWith(FakeUserModel(userId, "Proxy$userId"))
        return COROUTINE_SUSPENDED
    }
}

private class FakeBoxedResultUserDataSource {
    fun getUserByIdV2(
        userId: Long,
        continuation: FakeForeignContinuation,
    ): Any {
        continuation.resumeWith(Result.success(FakeUserModel(userId, "Boxed$userId")))
        return COROUTINE_SUSPENDED
    }
}

private class FakeNestedBoxedResultUserDataSource {
    fun getUserByIdV2(
        userId: Long,
        continuation: FakeForeignContinuation,
    ): Any {
        continuation.resumeWith(Result.success(Result.success(FakeUserModel(userId, "Nested$userId"))))
        return COROUTINE_SUSPENDED
    }
}

private class FakeSharedContinuationNestedResultUserDataSource {
    fun getUserByIdV2(
        userId: Long,
        continuation: Continuation<Any?>,
    ): Any {
        continuation.resumeWith(Result.success(Result.success(FakeUserModel(userId, "Shared$userId"))))
        return COROUTINE_SUSPENDED
    }
}

private class FakeGetNicknameUserModel(
    private val value: String,
) {
    fun getNickname(): String = value
}

private class FakeObfuscatedNicknameUserModel(
    private val v: String,
) {
    fun r(): String = v
}
