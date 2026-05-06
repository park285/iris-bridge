package party.qwer.iris.imagebridge.runtime.server

import android.net.LocalSocket
import java.io.InputStream
import java.io.OutputStream

internal interface BridgeMuxSocket : AutoCloseable {
    val inputStream: InputStream
    val outputStream: OutputStream
    val peerUid: Int?

    fun setReadTimeout(timeoutMs: Int)
}

internal class LocalBridgeMuxSocket(
    private val socket: LocalSocket,
) : BridgeMuxSocket {
    override val inputStream: InputStream
        get() = socket.inputStream

    override val outputStream: OutputStream
        get() = socket.outputStream

    override val peerUid: Int?
        get() = runCatching { socket.peerCredentials.uid }.getOrNull()

    override fun setReadTimeout(timeoutMs: Int) {
        runCatching {
            socket.javaClass
                .getMethod("setSoTimeout", Int::class.javaPrimitiveType)
                .invoke(socket, timeoutMs)
        }
    }

    override fun close() {
        socket.close()
    }
}
