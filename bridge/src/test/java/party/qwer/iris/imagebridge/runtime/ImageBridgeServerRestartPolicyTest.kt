@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageBridgeServerRestartPolicyTest {
    @Test
    fun `restart delay grows exponentially and caps`() {
        assertEquals(1_000L, ImageBridgeServer.nextBridgeRestartDelayMs(1))
        assertEquals(2_000L, ImageBridgeServer.nextBridgeRestartDelayMs(2))
        assertEquals(4_000L, ImageBridgeServer.nextBridgeRestartDelayMs(3))
        assertEquals(30_000L, ImageBridgeServer.nextBridgeRestartDelayMs(99))
    }

    @Test
    fun `client executor uses bounded pool and queue`() {
        val executor = ImageBridgeServer.newClientExecutorForTest()

        try {
            assertEquals(2, executor.corePoolSize)
            assertEquals(8, executor.maximumPoolSize)
            assertEquals(64, executor.queue.remainingCapacity())
        } finally {
            executor.shutdownNow()
        }
    }
}
