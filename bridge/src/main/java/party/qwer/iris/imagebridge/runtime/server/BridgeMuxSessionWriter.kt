package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeMuxFrame
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol

internal class BridgeMuxSessionWriter(
    private val client: BridgeMuxSocket,
    private val writeLock: Any,
    private val closeSession: () -> Unit,
) {
    fun writeResponse(
        correlationId: String,
        response: ImageBridgeProtocol.ImageBridgeResponse,
    ) {
        writeFrame(
            ImageBridgeMuxFrame(
                type = ImageBridgeMuxProtocol.TYPE_RESPONSE,
                correlationId = correlationId,
                response = response,
            ),
        )
    }

    fun writeProtocolFailure(
        correlationId: String?,
        message: String,
    ) {
        if (correlationId.isNullOrBlank()) {
            closeSession()
            return
        }
        writeResponse(
            correlationId,
            bridgeFailureResponse(
                error = message,
                errorCode = ImageBridgeProtocol.ERROR_BAD_REQUEST,
            ),
        )
    }

    fun writeFrame(frame: ImageBridgeMuxFrame) {
        synchronized(writeLock) {
            ImageBridgeMuxProtocol.writeFrame(client.outputStream, frame)
        }
    }
}
