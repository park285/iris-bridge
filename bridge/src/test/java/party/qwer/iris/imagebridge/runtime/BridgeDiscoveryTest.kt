@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeDiscoveryTest {
    @Test
    fun `instances keep discovery hook state isolated`() {
        val first = BridgeDiscovery()
        val second = BridgeDiscovery()

        first.markInstalledForTest(HOOK_SEND_MULTIPLE)
        first.recordForTest(HOOK_SEND_MULTIPLE, "uris=2")

        val firstHook = first.snapshot().hooks.first { it.name == HOOK_SEND_MULTIPLE }
        val secondHook = second.snapshot().hooks.first { it.name == HOOK_SEND_MULTIPLE }

        assertTrue(first.snapshot().installAttempted)
        assertTrue(firstHook.installed)
        assertEquals(1, firstHook.invocationCount)
        assertEquals("uris=2", firstHook.lastSummary)
        assertEquals(false, second.snapshot().installAttempted)
        assertEquals(false, secondHook.installed)
        assertEquals(0, secondHook.invocationCount)
        assertEquals(null, secondHook.lastSummary)
    }

    @Test
    fun `records discovery hook installation and invocation`() {
        defaultBridgeDiscovery.resetForTest()

        defaultBridgeDiscovery.markInstalledForTest(HOOK_SEND_MULTIPLE)
        defaultBridgeDiscovery.recordForTest(HOOK_SEND_MULTIPLE, "uris=2")

        val snapshot = defaultBridgeDiscovery.snapshot()
        val hook = snapshot.hooks.first { it.name == HOOK_SEND_MULTIPLE }

        assertTrue(snapshot.installAttempted)
        assertTrue(hook.installed)
        assertEquals(1, hook.invocationCount)
        assertEquals("uris=2", hook.lastSummary)
        assertNotNull(hook.lastSeenEpochMs)
    }
}
