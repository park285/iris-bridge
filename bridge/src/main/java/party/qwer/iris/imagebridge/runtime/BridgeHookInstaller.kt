package party.qwer.iris.imagebridge.runtime

import java.lang.reflect.Method

internal data class BridgeHookInvocation(
    val thisObject: Any?,
    val args: Array<Any?>,
)

internal interface BridgeHookInstaller {
    fun hookBefore(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    )

    fun hookAfter(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    )
}

internal object NoopBridgeHookInstaller : BridgeHookInstaller {
    override fun hookBefore(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) = Unit

    override fun hookAfter(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) = Unit
}
