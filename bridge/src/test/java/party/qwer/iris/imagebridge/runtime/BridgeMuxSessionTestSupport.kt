@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreMuxSession
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import party.qwer.iris.imagebridge.runtime.server.BRIDGE_MUX_DEFAULT_MAX_IN_FLIGHT
import party.qwer.iris.imagebridge.runtime.server.BridgeMuxSocket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal fun muxFrames(vararg frames: ImageBridgeMuxFrame): ByteArray =
    ByteArrayOutputStream()
        .also { output ->
            frames.forEach { frame -> ImageBridgeMuxProtocol.writeFrame(output, frame) }
        }.toByteArray()

internal class TestBridgeCoreMuxSession(
    val session: BridgeCoreMuxSession,
    private val runtime: BridgeCoreRuntime,
) : AutoCloseable {
    override fun close() {
        session.close()
        runtime.close()
    }
}

internal fun bridgeTestCoreRuntime(): BridgeCoreRuntime =
    checkNotNull(
        BridgeCore.loadOrNull(
            securityMode = "development",
            bridgeToken = "bridge-token",
            requireHandshakeRaw = "false",
        ),
    ) {
        "bridge-core host runtime unavailable"
    }

internal fun bridgeTestMuxSession(maxInFlight: Int = BRIDGE_MUX_DEFAULT_MAX_IN_FLIGHT): TestBridgeCoreMuxSession {
    val runtime = bridgeTestCoreRuntime()
    val session =
        checkNotNull(runtime.createMuxSession(maxInFlight)) {
            "bridge-core mux session unavailable"
        }
    return TestBridgeCoreMuxSession(session, runtime)
}

internal class FakeBridgeMuxSocket(
    input: ByteArray,
    override val peerUid: Int? = 0,
) : BridgeMuxSocket {
    override val inputStream = ByteArrayInputStream(input)
    override val outputStream = ByteArrayOutputStream()

    var readTimeoutMs: Int = 0
        private set
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) {
        readTimeoutMs = timeoutMs
    }

    override fun close() {
        closed = true
    }
}

internal class FailingOutputBridgeMuxSocket(
    input: ByteArray,
) : BridgeMuxSocket {
    override val inputStream = ByteArrayInputStream(input)
    override val outputStream =
        object : OutputStream() {
            override fun write(byte: Int) = throw IOException("socket not created")
        }
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

internal class TimeoutBridgeMuxSocket : BridgeMuxSocket {
    override val inputStream: InputStream =
        object : InputStream() {
            override fun read(): Int = throw IOException("Try again")
        }
    override val outputStream = ByteArrayOutputStream()
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

internal class PartialPayloadTimeoutBridgeMuxSocket : BridgeMuxSocket {
    override val inputStream: InputStream =
        object : InputStream() {
            private val header = byteArrayOf(0, 0, 0, 16)
            private var index = 0

            override fun read(): Int {
                if (index < header.size) {
                    return header[index++].toInt() and 0xff
                }
                throw IOException("Try again")
            }
        }
    override val outputStream = ByteArrayOutputStream()
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

internal class PartialLengthTimeoutBridgeMuxSocket : BridgeMuxSocket {
    override val inputStream: InputStream =
        object : InputStream() {
            private var firstRead = true

            override fun read(): Int {
                if (firstRead) {
                    firstRead = false
                    return 0
                }
                throw IOException("Try again")
            }
        }
    override val outputStream = ByteArrayOutputStream()
    override val peerUid: Int? = 0
    var closed = false
        private set

    override fun setReadTimeout(timeoutMs: Int) = Unit

    override fun close() {
        closed = true
    }
}

internal class DirectExecutorService : AbstractExecutorService() {
    private val shutdown = AtomicBoolean(false)

    override fun shutdown() {
        shutdown.set(true)
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown.set(true)
        return emptyList()
    }

    override fun isShutdown(): Boolean = shutdown.get()

    override fun isTerminated(): Boolean = shutdown.get()

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = true

    override fun execute(command: Runnable) {
        if (shutdown.get()) {
            error("executor shut down")
        }
        command.run()
    }
}

internal class QueuedExecutorService : AbstractExecutorService() {
    private val shutdown = AtomicBoolean(false)
    private val tasks = ArrayDeque<Runnable>()

    override fun shutdown() {
        shutdown.set(true)
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown.set(true)
        return tasks.toList().also { tasks.clear() }
    }

    override fun isShutdown(): Boolean = shutdown.get()

    override fun isTerminated(): Boolean = shutdown.get() && tasks.isEmpty()

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = isTerminated

    override fun execute(command: Runnable) {
        if (shutdown.get()) {
            error("executor shut down")
        }
        tasks += command
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    fun hasPendingTasks(): Boolean = tasks.isNotEmpty()
}
