@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import party.qwer.iris.imagebridge.runtime.server.defaultImageBridgeServer
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageBridgeServerRestartPolicyTest {
    @Test
    fun `instances keep restart failure state isolated`() {
        val first = ImageBridgeServer()
        val second = ImageBridgeServer()

        first.recordServerFailureForTest("first crash")

        assertEquals(1, first.healthSnapshotForTest().restartCount)
        assertEquals("first crash", first.healthSnapshotForTest().lastCrashMessage)
        assertEquals(0, second.healthSnapshotForTest().restartCount)
        assertEquals(null, second.healthSnapshotForTest().lastCrashMessage)
    }

    @Test
    fun `restart delay grows exponentially and caps`() {
        assertEquals(1_000L, defaultImageBridgeServer.nextBridgeRestartDelayMs(1))
        assertEquals(2_000L, defaultImageBridgeServer.nextBridgeRestartDelayMs(2))
        assertEquals(4_000L, defaultImageBridgeServer.nextBridgeRestartDelayMs(3))
        assertEquals(30_000L, defaultImageBridgeServer.nextBridgeRestartDelayMs(99))
    }

    @Test
    fun `client executor uses bounded pool and queue`() {
        val executor = defaultImageBridgeServer.newClientExecutorForTest()

        try {
            assertEquals(2, executor.corePoolSize)
            assertEquals(8, executor.maximumPoolSize)
            assertEquals(64, executor.queue.remainingCapacity())
        } finally {
            executor.shutdownNow()
        }
    }
}
