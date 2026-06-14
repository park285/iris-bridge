package party.qwer.iris

import party.qwer.iris.generated.GeneratedBridgeProtocolContract
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.ByteBuffer

enum class FrameReadStage {
    LENGTH,
    PAYLOAD,
}

class FrameReadTimeoutException(
    val stage: FrameReadStage,
    val bytesRead: Int,
    cause: IOException,
) : IOException("frame read timed out while reading ${stage.name.lowercase()}", cause)

object LengthPrefixedFrameCodec {
    const val MAX_FRAME_SIZE = GeneratedBridgeProtocolContract.MAX_FRAME_SIZE

    fun writePayload(
        output: OutputStream,
        payload: String,
    ) = writePayloadBytes(output, payload.toByteArray(Charsets.UTF_8))

    fun writePayloadBytes(
        output: OutputStream,
        bytes: ByteArray,
    ) {
        require(bytes.size in 1..MAX_FRAME_SIZE) { "invalid frame size: ${bytes.size}" }
        val dos = DataOutputStream(output)
        dos.writeInt(bytes.size)
        dos.write(bytes)
        dos.flush()
    }

    fun readPayload(input: InputStream): String {
        val sizeBytes = ByteArray(Int.SIZE_BYTES)
        readFully(input, sizeBytes, FrameReadStage.LENGTH)
        val size = ByteBuffer.wrap(sizeBytes).int
        require(size in 1..MAX_FRAME_SIZE) { "invalid frame size: $size" }
        val bytes = ByteArray(size)
        readFully(input, bytes, FrameReadStage.PAYLOAD)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readFully(
        input: InputStream,
        buffer: ByteArray,
        stage: FrameReadStage,
    ) {
        var offset = 0
        while (offset < buffer.size) {
            val read =
                try {
                    input.read(buffer, offset, buffer.size - offset)
                } catch (error: InterruptedIOException) {
                    throw FrameReadTimeoutException(stage, offset, error)
                } catch (error: IOException) {
                    if (error.isTimeoutLike()) {
                        throw FrameReadTimeoutException(stage, offset, error)
                    }
                    throw error
                }
            if (read < 0) {
                throw EOFException()
            }
            offset += read
        }
    }

    private fun IOException.isTimeoutLike(): Boolean =
        message?.contains("try again", ignoreCase = true) == true ||
            message?.contains("timed out", ignoreCase = true) == true
}
