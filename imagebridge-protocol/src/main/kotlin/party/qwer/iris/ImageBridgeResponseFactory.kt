package party.qwer.iris

interface ImageBridgeResponseFactory {
    fun buildSuccessResponse(requestId: String? = null): ImageBridgeResponse =
        ImageBridgeResponse(
            status = ImageBridgeProtocol.STATUS_SENT,
            requestId = requestId,
        )

    fun buildFailureResponse(
        error: String,
        errorCode: String? = null,
        requestId: String? = null,
    ): ImageBridgeResponse =
        ImageBridgeResponse(
            status = ImageBridgeProtocol.STATUS_FAILED,
            error = error,
            errorCode = errorCode,
            requestId = requestId,
        )
}
