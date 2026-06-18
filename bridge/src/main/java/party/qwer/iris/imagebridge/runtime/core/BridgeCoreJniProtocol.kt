package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniProtocol {
    fun nativeProtocolContractJson(): String = BridgeCoreJniDispatcher.envelope("protocol.contractJson")
}
