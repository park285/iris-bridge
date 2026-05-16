package party.qwer.iris.imagebridge.runtime.discovery

import android.util.Log
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.BridgeHookInvocation
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "IrisBridge"

private const val HOOK_ROOM_DAO = "MasterDatabase#roomDao"
private const val HOOK_MANAGER_DIRECT = "ChatRoomManager#directResolve"
private const val HOOK_MANAGER_BROAD = "ChatRoomManager#broadResolve"
internal const val HOOK_REPLY_MARKDOWN_INGRESS = "ReplyMarkdown#ingress"
internal const val HOOK_REPLY_MARKDOWN_REUSE = "ReplyMarkdown#reuseIntent"
internal const val HOOK_REPLY_MARKDOWN_REQUEST = "ReplyMarkdown#requestDispatch"
internal const val HOOK_REPLY_LEVERAGE_COMMIT = "ReplyLeverage#chatLogCommit"
internal const val HOOK_SEND_SINGLE = "ChatMediaSender#sendSingle"
internal const val HOOK_SEND_MULTIPLE = "ChatMediaSender#sendMultiple"
internal const val HOOK_SEND_THREADED_ENTRY = "ChatMediaSender#threadedEntry"
internal const val HOOK_SEND_THREADED_INJECT = "ChatMediaSender#threadedInject"

internal object BridgeDiscovery {
    private val installStarted = AtomicBoolean(false)
    private val installAttempted = AtomicBoolean(false)
    private val states =
        listOf(
            HOOK_ROOM_DAO,
            HOOK_MANAGER_DIRECT,
            HOOK_MANAGER_BROAD,
            HOOK_REPLY_MARKDOWN_INGRESS,
            HOOK_REPLY_MARKDOWN_REUSE,
            HOOK_REPLY_MARKDOWN_REQUEST,
            HOOK_REPLY_LEVERAGE_COMMIT,
            HOOK_SEND_SINGLE,
            HOOK_SEND_MULTIPLE,
            HOOK_SEND_THREADED_ENTRY,
            HOOK_SEND_THREADED_INJECT,
        ).associateWith { DiscoveryHookState() }.toMutableMap()

    fun install(
        registry: KakaoClassRegistry,
        hookInstaller: BridgeHookInstaller,
    ) {
        installAttempted.set(true)
        if (!installStarted.compareAndSet(false, true)) return
        installMethodHook(HOOK_ROOM_DAO, registry.roomDaoMethod, hookInstaller) {
            "roomDao requested"
        }

        installMethodHook(HOOK_MANAGER_DIRECT, registry.directRoomResolverMethod, hookInstaller) { param ->
            "roomId=${param.args.getOrNull(0)}"
        }

        installMethodHook(HOOK_MANAGER_BROAD, registry.broadRoomResolverMethod, hookInstaller) { param ->
            "roomId=${param.args.getOrNull(0)} includeMembers=${param.args.getOrNull(1)} includeOpenLink=${param.args.getOrNull(2)}"
        }

        installMethodHook(HOOK_SEND_SINGLE, registry.singleSendMethod, hookInstaller) { param ->
            "mediaItem=${param.args.getOrNull(0)} suppressAnimation=${param.args.getOrNull(1)}"
        }

        installMethodHook(HOOK_SEND_MULTIPLE, registry.multiSendMethod, hookInstaller) { param ->
            val uriCount = (param.args.getOrNull(0) as? List<*>)?.size ?: -1
            "uris=$uriCount type=${param.args.getOrNull(1)} shareOriginal=${param.args.getOrNull(6)} highQuality=${param.args.getOrNull(7)}"
        }
    }

    fun snapshot(): BridgeDiscoverySnapshot =
        BridgeDiscoverySnapshot(
            installAttempted = installAttempted.get(),
            hooks = states.entries.map { (name, state) -> state.toSnapshot(name) },
        )

    internal fun resetForTest() {
        installStarted.set(false)
        installAttempted.set(false)
        states.values.forEach { it.reset() }
    }

    internal fun recordForTest(
        hookName: String,
        summary: String,
    ) {
        record(hookName, summary)
    }

    internal fun markInstalledForTest(hookName: String) {
        installAttempted.set(true)
        stateFor(hookName).markInstalled()
    }

    internal fun markInstalled(hookName: String) {
        installAttempted.set(true)
        stateFor(hookName).markInstalled()
    }

    internal fun markInstallError(
        hookName: String,
        detail: String,
    ) {
        installAttempted.set(true)
        stateFor(hookName).markInstallError(detail)
    }

    internal fun recordHook(
        hookName: String,
        summary: String,
    ) {
        installAttempted.set(true)
        record(hookName, summary)
    }

    private fun installMethodHook(
        hookName: String,
        method: Method,
        hookInstaller: BridgeHookInstaller,
        summarize: (BridgeHookInvocation) -> String,
    ) = installDiscoveryMethodHook(hookName, method, hookInstaller, stateFor(hookName), summarize, ::record)

    private fun record(
        hookName: String,
        summary: String,
    ) {
        stateFor(hookName).record(summary)
    }

    private fun stateFor(hookName: String): DiscoveryHookState = states.computeIfAbsent(hookName) { DiscoveryHookState() }
}

private fun installDiscoveryMethodHook(
    hookName: String,
    method: Method,
    hookInstaller: BridgeHookInstaller,
    state: DiscoveryHookState,
    summarize: (BridgeHookInvocation) -> String,
    record: (String, String) -> Unit,
) {
    if (Modifier.isAbstract(method.modifiers)) {
        state.markInstallError("abstract method, skipped: ${method.declaringClass.name}.${method.name}")
        Log.w(TAG, "discovery hook skipped (abstract): $hookName — ${method.declaringClass.name}.${method.name}")
        return
    }
    try {
        hookInstaller.hookBefore(method) { invocation ->
            record(hookName, summarize(invocation))
        }
        state.markInstalled()
    } catch (error: Throwable) {
        state.markInstallError(error.message ?: error.javaClass.name)
        Log.e(TAG, "discovery hook install failed: $hookName", error)
    }
}
