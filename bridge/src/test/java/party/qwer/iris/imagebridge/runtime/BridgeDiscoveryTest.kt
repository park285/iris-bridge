@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeDiscoveryTest {
    @Test
    fun `records discovery hook installation and invocation`() {
        BridgeDiscovery.resetForTest()

        BridgeDiscovery.markInstalledForTest(HOOK_SEND_MULTIPLE)
        BridgeDiscovery.recordForTest(HOOK_SEND_MULTIPLE, "uris=2")

        val snapshot = BridgeDiscovery.snapshot()
        val hook = snapshot.hooks.first { it.name == HOOK_SEND_MULTIPLE }

        assertTrue(snapshot.installAttempted)
        assertTrue(hook.installed)
        assertEquals(1, hook.invocationCount)
        assertEquals("uris=2", hook.lastSummary)
        assertNotNull(hook.lastSeenEpochMs)
    }
}
