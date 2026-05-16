@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModernXposedPackagingTest {
    @Test
    fun `bridge module uses modern LSPosed entry metadata without legacy xposed assets`() {
        val projectDir = File(".").canonicalFile
        val legacyInit = projectDir.resolve("src/main/assets/xposed_init")
        val manifest = projectDir.resolve("src/main/AndroidManifest.xml").readText()
        val xposedMetaDir = projectDir.resolve("src/main/resources/META-INF/xposed")

        assertFalse(legacyInit.exists(), "legacy assets/xposed_init must not be packaged")
        assertFalse(manifest.contains("xposedmodule"), "legacy xposedmodule manifest metadata must not be packaged")
        assertFalse(manifest.contains("xposedminversion"), "legacy xposedminversion manifest metadata must not be packaged")
        assertFalse(manifest.contains("xposedscope"), "legacy xposedscope manifest metadata must not be packaged")

        assertTrue(
            xposedMetaDir.resolve("java_init.list").readText().contains(
                "party.qwer.iris.imagebridge.runtime.IrisBridgeModule",
            ),
        )
        assertTrue(
            xposedMetaDir
                .resolve("scope.list")
                .readText()
                .lineSequence()
                .any { it.trim() == "com.kakao.talk" },
        )

        val moduleProp = xposedMetaDir.resolve("module.prop").readText()
        assertTrue(moduleProp.contains("minApiVersion=101"))
        assertTrue(moduleProp.contains("targetApiVersion=101"))
        assertTrue(moduleProp.contains("exceptionMode=protective"))
        assertFalse(
            moduleProp.lineSequence().any { it.trim() == "staticScope=true" },
            "static scope declaration must not be packaged; users and iris_control can still manage LSPosed scope",
        )
    }
}
