@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.server

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ImageBridgeServerBridgeCoreGateTest {
    @Test
    fun `start refuses to run when bridge core is unavailable and exposes it in health`() {
        val server = ImageBridgeServer()

        server.start(context = RuntimeEnvironment.getApplication(), registry = null, bridgeCore = null)

        val snapshot = server.healthSnapshotForTest()
        assertFalse(snapshot.running, "mux server must not run without bridge-core")
        assertTrue(snapshot.bridgeCoreUnavailable, "health must surface bridgeCoreUnavailable")
        assertEquals(true, snapshot.toJson().getBoolean("bridgeCoreUnavailable"))
    }

    @Test
    fun `start runs when bridge core is present and health does not flag unavailability`() {
        val server = ImageBridgeServer()
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )

        server.start(context = RuntimeEnvironment.getApplication(), registry = null, bridgeCore = runtime)
        try {
            val snapshot = server.healthSnapshotForTest()
            assertTrue(snapshot.running, "mux server must run when bridge-core is present")
            assertFalse(snapshot.bridgeCoreUnavailable)
            assertFalse(snapshot.toJson().getBoolean("bridgeCoreUnavailable"))
        } finally {
            server.stopForTest()
        }
    }
}
