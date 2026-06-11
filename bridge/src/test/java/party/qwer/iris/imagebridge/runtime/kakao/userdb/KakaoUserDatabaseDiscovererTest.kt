@file:Suppress("FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KakaoUserDatabaseDiscovererTest {
    @Test
    fun `discoverKakaoUserDatabaseAccess returns null without scan when known class is absent`() {
        val access =
            discoverKakaoUserDatabaseAccess(
                checkNotNull(KakaoUserDatabaseDiscovererTest::class.java.classLoader),
                scanFallback = false,
            )

        assertNull(access)
    }

    @Test
    fun `findGetUserByIdV2Method accepts Kotlin mangled Result suspend method`() {
        val method = findGetUserByIdV2MethodForTest(FakeMangledUserDataSource::class.java)

        assertNotNull(method)
        assertEquals("getUserByIdV2-gIAlu-s", method.name)
    }

    @Test
    fun `findGetUserByIdV2Method accepts obfuscated method when debug metadata identifies getUserByIdV2`() {
        val method = findGetUserByIdV2MethodForTest(FakeObfuscatedUserDataSource::class.java)

        assertNotNull(method)
        assertEquals("t", method.name)
    }

    @Test
    fun `findGetUserByIdV2Method rejects obfuscated method without debug metadata`() {
        val method = findGetUserByIdV2MethodForTest(FakeObfuscatedWithoutMetadataUserDataSource::class.java)

        assertNull(method)
    }

    @Test
    fun `findGetUserByIdV2Method rejects obfuscated method with wrong debug metadata`() {
        val method = findGetUserByIdV2MethodForTest(FakeObfuscatedWrongMetadataUserDataSource::class.java)

        assertNull(method)
    }

    @Test
    fun `findGetUserByIdV2Method rejects debug metadata when obfuscated method is absent`() {
        val method = findGetUserByIdV2MethodForTest(FakeWrongObfuscatedNameUserDataSource::class.java)

        assertNull(method)
    }

    @Test
    fun `findGetUserByIdV2Method prefers exact name over obfuscated fallback`() {
        val method = findGetUserByIdV2MethodForTest(FakeExactAndObfuscatedUserDataSource::class.java)

        assertNotNull(method)
        assertEquals("getUserByIdV2", method.name)
    }
}

private fun findGetUserByIdV2MethodForTest(clazz: Class<*>): java.lang.reflect.Method? {
    val holder =
        Class.forName(
            "party.qwer.iris.imagebridge.runtime.kakao.userdb.KakaoUserDatabaseDiscovererKt",
        )
    val method =
        holder.declaredMethods.first { declared ->
            declared.name == "findGetUserByIdV2Method"
        }
    method.isAccessible = true
    return method.invoke(null, clazz) as java.lang.reflect.Method?
}
