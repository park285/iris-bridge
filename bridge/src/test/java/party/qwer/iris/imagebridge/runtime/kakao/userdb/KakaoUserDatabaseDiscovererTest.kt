@file:Suppress("FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoUserDatabaseDiscovererTest {
    @Test
    fun `discoverKakaoUserDatabaseAccess returns null without scan when known class is absent`() {
        val access =
            discoverKakaoUserDatabaseAccess(
                URLClassLoader(emptyArray(), null),
                scanFallback = false,
            )

        assertNull(access)
    }

    @Test
    fun `discoverKakaoUserDatabaseAccess resolves KakaoTalk 26_4_2 obfuscated UserDatabaseDataSource`() {
        val access =
            discoverKakaoUserDatabaseAccess(
                checkNotNull(KakaoUserDatabaseDiscovererTest::class.java.classLoader),
                scanFallback = false,
            )

        assertNotNull(access)
        assertEquals("X20.C36045r2", access.singleton.javaClass.name)
        assertEquals("m113412t", access.getUserByIdV2Method.name)
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
    fun `findGetUserByIdV2Method accepts jadx-style obfuscated method when debug metadata identifies getUserByIdV2`() {
        val method = findGetUserByIdV2MethodForTest(FakeJadxObfuscatedUserDataSource::class.java)

        assertNotNull(method)
        assertEquals("m113412t", method.name)
    }

    @Test
    fun `findGetUserByIdV2Method accepts R8 debug metadata accessor names`() {
        val method = findGetUserByIdV2MethodForTest(FakeR8MetadataObfuscatedUserDataSource::class.java)

        assertNotNull(method)
        assertEquals("m113412t", method.name)
    }

    @Test
    fun `findGetUserByIdV2Method accepts known 26_4_2 runtime class when debug metadata is unavailable`() {
        val method = findGetUserByIdV2MethodForTest(Class.forName("X20.r2"))

        assertNotNull(method)
        assertEquals("t", method.name)
    }

    @Test
    fun `resolveUserDatabaseSingleton accepts package-private companion default factory`() {
        val singleton = resolveUserDatabaseSingleton(Class.forName("X20.r2"))

        assertNotNull(singleton)
        assertEquals("X20.r2", singleton.javaClass.name)
    }

    @Test
    fun `resolveUserDatabaseSingleton accepts explicit companion factory dependencies`() {
        val singleton =
            resolveUserDatabaseSingleton(
                Class.forName("X20.ExplicitFactoryDataSource"),
                dependencies = listOf(Any(), Any()),
            )

        assertNotNull(singleton)
        assertEquals("X20.ExplicitFactoryDataSource", singleton.javaClass.name)
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

    @Test
    fun `findGetUserByIdV2Method accepts Continuation loaded by target classloader`() {
        val clazz = compileForeignContinuationUserDataSource()

        val method = findGetUserByIdV2MethodForTest(clazz)

        assertNotNull(method)
        assertEquals("getUserByIdV2", method.name)
        assertEquals("kotlin.coroutines.Continuation", method.parameterTypes[1].name)
        assertTrue(!kotlin.coroutines.Continuation::class.java.isAssignableFrom(method.parameterTypes[1]))
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

private fun compileForeignContinuationUserDataSource(): Class<*> {
    val tempDir = Files.createTempDirectory("iris-userdb-continuation-test")
    val sourceRoot = tempDir.resolve("src")
    val outputRoot = tempDir.resolve("out")
    Files.createDirectories(sourceRoot.resolve("kotlin/coroutines"))
    Files.createDirectories(sourceRoot.resolve("fake"))
    Files.createDirectories(outputRoot)
    val continuationSource = sourceRoot.resolve("kotlin/coroutines/Continuation.java")
    val dataSourceSource = sourceRoot.resolve("fake/CrossLoaderUserDataSource.java")
    continuationSource.toFile().writeText(
        """
        package kotlin.coroutines;
        public interface Continuation {
        }
        """.trimIndent(),
    )
    dataSourceSource.toFile().writeText(
        """
        package fake;
        public final class CrossLoaderUserDataSource {
            public Object getUserByIdV2(long userId, kotlin.coroutines.Continuation continuation) {
                return null;
            }
        }
        """.trimIndent(),
    )
    val compiler = ToolProvider.getSystemJavaCompiler()
    assertNotNull(compiler, "JDK compiler is required for this test")
    val result =
        compiler.run(
            null,
            null,
            null,
            "-classpath",
            "",
            "-d",
            outputRoot.toString(),
            continuationSource.toString(),
            dataSourceSource.toString(),
        )
    assertEquals(0, result)
    val loader = URLClassLoader(arrayOf(outputRoot.toUri().toURL()), null)
    return Class.forName("fake.CrossLoaderUserDataSource", false, loader)
}
