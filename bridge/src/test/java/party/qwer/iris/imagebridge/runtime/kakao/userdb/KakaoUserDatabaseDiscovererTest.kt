@file:Suppress("FunctionName")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import kotlin.test.Test
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
}
