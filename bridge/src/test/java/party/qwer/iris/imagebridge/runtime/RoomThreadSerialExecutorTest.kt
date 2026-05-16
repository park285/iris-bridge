@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.send.RoomThreadSerialExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomThreadSerialExecutorTest {
    @Test
    fun `same room and thread are serialized`() {
        val executor = RoomThreadSerialExecutor()
        val releaseFirst = CountDownLatch(1)
        val firstStarted = CountDownLatch(1)
        val secondRan = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first =
                pool.submit<Unit> {
                    executor.executeSynchronously(roomId = 1L, threadId = 10L) {
                        firstStarted.countDown()
                        releaseFirst.await(3, TimeUnit.SECONDS)
                    }
                }
            val second =
                pool.submit<Unit> {
                    firstStarted.await(3, TimeUnit.SECONDS)
                    executor.executeSynchronously(roomId = 1L, threadId = 10L) {
                        secondRan.set(true)
                    }
                }

            assertFalse(secondRan.get())
            releaseFirst.countDown()
            first.get(3, TimeUnit.SECONDS)
            second.get(3, TimeUnit.SECONDS)
            assertTrue(secondRan.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `different threads in same room can run independently`() {
        val executor = RoomThreadSerialExecutor()
        val releaseFirst = CountDownLatch(1)
        val firstStarted = CountDownLatch(1)
        val secondRan = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first =
                pool.submit<Unit> {
                    executor.executeSynchronously(roomId = 1L, threadId = 10L) {
                        firstStarted.countDown()
                        releaseFirst.await(3, TimeUnit.SECONDS)
                    }
                }
            val second =
                pool.submit<Unit> {
                    firstStarted.await(3, TimeUnit.SECONDS)
                    executor.executeSynchronously(roomId = 1L, threadId = 11L) {
                        secondRan.set(true)
                    }
                }

            second.get(3, TimeUnit.SECONDS)
            assertTrue(secondRan.get())
            releaseFirst.countDown()
            first.get(3, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `lock count stays bounded across many keys`() {
        val executor = RoomThreadSerialExecutor(stripeCount = 8)

        repeat(100) { index ->
            executor.executeSynchronously(roomId = index.toLong(), threadId = index.toLong()) {}
        }

        assertEquals(8, executor.lockCountForTest())
    }
}
