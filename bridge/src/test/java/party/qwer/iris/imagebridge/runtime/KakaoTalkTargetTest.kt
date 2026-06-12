@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTarget
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTargetContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KakaoTalkTargetTest {
    @Test
    fun `supported packages include official and revanced`() {
        assertTrue(KakaoTalkTarget.isSupported("com.kakao.talk"))
        assertTrue(KakaoTalkTarget.isSupported("com.kakao.talk.revanced"))
    }

    @Test
    fun `revanced target keeps dex package on official namespace`() {
        val target = KakaoTalkTarget.resolve("com.kakao.talk.revanced")

        assertEquals("com.kakao.talk.revanced", target.packageName)
        assertEquals("com.kakao.talk", target.dexPackage)
        assertEquals("com.kakao.talk.manager.ShareManager", target.dexClassName("manager.ShareManager"))
        assertEquals(
            "/data/data/com.kakao.talk.revanced/databases/KakaoTalk.db",
            target.dataPath("databases/KakaoTalk.db"),
        )
    }

    @Test
    fun `resolve uses native target policy when available`() {
        val target =
            KakaoTalkTarget.resolve("com.kakao.talk.revanced") { packageName ->
                KakaoTalkTargetContext(
                    packageName = packageName,
                    dexPackage = "native.dex.package",
                )
            }

        assertEquals("com.kakao.talk.revanced", target.packageName)
        assertEquals("native.dex.package", target.dexPackage)
        assertEquals(
            "native.dex.package.manager.ShareManager",
            target.dexClassName("manager.ShareManager"),
        )
    }

    @Test
    fun `resolve fails closed when native target policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                KakaoTalkTarget.resolve("com.kakao.talk.revanced") { null }
            }

        assertEquals("bridge core unavailable to resolve KakaoTalk target", error.message)
    }

    @Test
    fun `unsupported package is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            KakaoTalkTarget.resolve("com.example.talk")
        }
    }
}
