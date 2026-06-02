@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.server.BoundedBridgeSessionAdmission
import party.qwer.iris.imagebridge.runtime.server.BridgeMetrics
import party.qwer.iris.imagebridge.runtime.server.BridgeMuxClientDispatcher
import party.qwer.iris.imagebridge.runtime.server.BridgePeerIdentityValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeSecurityMode
import party.qwer.iris.imagebridge.runtime.server.BridgeSessionAdmission
import party.qwer.iris.imagebridge.runtime.server.BridgeSocketHandshakeAuthenticator
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import party.qwer.iris.imagebridge.runtime.server.RawThreadBridgeSessionAdmission
import party.qwer.iris.imagebridge.runtime.server.newBridgeSessionExecutor
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BridgeMuxClientDispatcherAdmissionTest {
    @Test
    fun `bounded admission accepts within cap and rejects beyond cap`() {
        val cap = 2
        val executor = newBridgeSessionExecutor(cap)
        val admission = BoundedBridgeSessionAdmission(executor = executor, maxConcurrentSessions = cap)
        val hold = CountDownLatch(1)
        val started = CountDownLatch(cap)
        try {
            repeat(cap) {
                val accepted =
                    admission.tryExecute {
                        started.countDown()
                        hold.await()
                    }
                assertTrue(accepted, "session within cap must be admitted")
            }
            assertTrue(started.await(2, TimeUnit.SECONDS), "admitted sessions must start")
            assertEquals(cap, admission.activeSessionCount())

            val overflowRan = CountDownLatch(1)
            val overflow = admission.tryExecute { overflowRan.countDown() }
            assertFalse(overflow, "session beyond cap must be rejected")
            assertFalse(overflowRan.await(200, TimeUnit.MILLISECONDS), "rejected work must not run")
        } finally {
            hold.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `bounded admission releases capacity after work completes`() {
        val cap = 1
        val executor = newBridgeSessionExecutor(cap)
        val admission = BoundedBridgeSessionAdmission(executor = executor, maxConcurrentSessions = cap)
        try {
            val firstRan = CountDownLatch(1)
            assertTrue(admission.tryExecute { firstRan.countDown() })
            assertTrue(firstRan.await(2, TimeUnit.SECONDS), "first work must run")
            awaitActiveSessions(admission, expected = 0)

            val secondRan = CountDownLatch(1)
            assertTrue(admission.tryExecute { secondRan.countDown() }, "capacity must be reusable after completion")
            assertTrue(secondRan.await(2, TimeUnit.SECONDS), "second work must run")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `dispatcher sends bridge busy goaway when admission rejects session`() {
        val metrics = BridgeMetrics()
        val socket = FakeBridgeMuxSocket(input = ByteArray(0))
        val dispatcher = dispatcher(metrics, RejectingSessionAdmission)

        dispatcher.dispatch(socket)

        val frame = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(socket.outputStream.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_GOAWAY, frame.type)
        assertEquals(ImageBridgeProtocol.ERROR_BRIDGE_BUSY, frame.errorCode)
        assertTrue(socket.closed, "rejected client socket must be closed")
        assertEquals(1, metrics.snapshot().bridgeBusy)
        assertEquals(1, metrics.snapshot().rejectedClient)
    }

    @Test
    fun `dispatcher runs session through admission when admitted`() {
        val metrics = BridgeMetrics()
        val socket =
            FakeBridgeMuxSocket(
                input =
                    muxFrames(
                        ImageBridgeMuxFrame(
                            type = ImageBridgeMuxProtocol.TYPE_REQUEST,
                            correlationId = "corr-admit",
                            request = ImageBridgeProtocol.buildHealthRequest(),
                        ),
                    ),
            )
        val dispatcher = dispatcher(metrics, InlineSessionAdmission)

        dispatcher.dispatch(socket)

        val frame = ImageBridgeMuxProtocol.readFrame(ByteArrayInputStream(socket.outputStream.toByteArray()))
        assertEquals(ImageBridgeMuxProtocol.TYPE_RESPONSE, frame.type)
        assertEquals("corr-admit", frame.correlationId)
        assertEquals(ImageBridgeProtocol.STATUS_OK, frame.response?.status)
        assertEquals(0, metrics.snapshot().bridgeBusy)
    }

    @Test
    fun `raw thread admission path runs session work`() {
        val admission = RawThreadBridgeSessionAdmission()
        val ran = CountDownLatch(1)
        assertTrue(admission.tryExecute { ran.countDown() })
        assertTrue(ran.await(2, TimeUnit.SECONDS), "raw-thread rollback path must run work")
    }

    private fun dispatcher(
        metrics: BridgeMetrics,
        admission: BridgeSessionAdmission,
    ): BridgeMuxClientDispatcher =
        BridgeMuxClientDispatcher(
            executorProvider = { DirectExecutorService() },
            handlerProvider = {
                ImageBridgeRequestHandler(
                    imageSender = { error("should not be called") },
                    healthProvider = { readyHealthSnapshot() },
                    handshakeValidator = developmentHandshakeValidator(),
                )
            },
            isRunning = { true },
            peerIdentityValidator = BridgePeerIdentityValidator(allowedUids = setOf(0)),
            metrics = metrics,
            sessionAdmission = admission,
            handshakeAuthenticator =
                BridgeSocketHandshakeAuthenticator(
                    expectedToken = "",
                    securityMode = BridgeSecurityMode.DEVELOPMENT,
                    requireHandshakeRaw = "false",
                ),
            socketNameProvider = { "test-mux" },
        )

    private fun awaitActiveSessions(
        admission: BoundedBridgeSessionAdmission,
        expected: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (admission.activeSessionCount() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assertEquals(expected, admission.activeSessionCount())
    }

    private object RejectingSessionAdmission : BridgeSessionAdmission {
        override fun tryExecute(work: Runnable): Boolean = false

        override fun activeSessionCount(): Int = 0
    }

    private object InlineSessionAdmission : BridgeSessionAdmission {
        override fun tryExecute(work: Runnable): Boolean {
            work.run()
            return true
        }

        override fun activeSessionCount(): Int = 0
    }
}
