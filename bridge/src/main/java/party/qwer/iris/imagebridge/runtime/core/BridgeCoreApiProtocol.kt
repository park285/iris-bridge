package party.qwer.iris.imagebridge.runtime.core

fun BridgeCore.protocolContractJson(): String? {
    if (!bridgeCoreLoadLibraryOnce()) return null
    return runCatching {
        val envelope = BridgeCoreEnvelope.parse(BridgeCoreJniProtocol.nativeProtocolContractJson())
        if (envelope.isOk) {
            envelope.string("contractJson")
        } else {
            null
        }
    }.getOrElse { error ->
        bridgeCoreLogError("bridge-core protocol contract dispatch threw", error)
        null
    }
}
