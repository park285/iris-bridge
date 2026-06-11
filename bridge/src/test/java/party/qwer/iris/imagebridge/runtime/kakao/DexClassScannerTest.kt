package party.qwer.iris.imagebridge.runtime.kakao

import kotlin.test.Test
import kotlin.test.assertEquals

class DexClassScannerTest {
    @Test
    fun `findAll skips classes whose signatures reference unavailable platform APIs`() {
        val scanner =
            DexClassScanner(
                classLoader = javaClass.classLoader!!,
                classNameProvider = {
                    listOf(
                        org.junit.Test::class.java.name,
                        org.junit.Assert::class.java.name,
                    )
                },
            )

        val matches =
            scanner.findAll { clazz ->
                if (clazz == org.junit.Test::class.java) {
                    throw NoClassDefFoundError("android/app/Activity\$ScreenCaptureCallback")
                }
                clazz == org.junit.Assert::class.java
            }

        assertEquals(listOf(org.junit.Assert::class.java), matches)
    }
}
