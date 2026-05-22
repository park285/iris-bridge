package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_THREADED_INJECT
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal data class ThreadedImagePendingContext(
    val roomId: Long,
    val threadId: Long,
    val threadScope: Int,
)

internal object ThreadedImageXposedInjector {
    private const val TAG = "IrisBridge"
    private const val REQUEST_COMPANION_CLASS = "com.kakao.talk.manager.send.ChatSendingLogRequest\$a"
    private val installed = AtomicBoolean(false)
    private val pendingContext = ThreadLocal<ThreadedImagePendingContext?>()

    internal data class InjectHookBinding(
        val method: Method,
        val sendingLogArgIndex: Int,
        val source: String,
    )

    fun install(
        registry: KakaoClassRegistry,
        hookInstaller: BridgeHookInstaller,
    ) {
        if (!installed.compareAndSet(false, true)) return
        val bindings =
            runCatching {
                selectThreadedImageInjectBindings(
                    requestCompanionClass =
                        runCatching {
                            Class.forName(
                                REQUEST_COMPANION_CLASS,
                                false,
                                registry.chatMediaSenderClass.classLoader,
                            )
                        }.getOrNull(),
                    chatMediaSenderClass = registry.chatMediaSenderClass,
                    chatRoomClass = registry.chatRoomClass,
                    writeTypeClass = registry.writeTypeClass,
                    listenerClass = registry.listenerClass,
                )
            }.onFailure { error ->
                defaultBridgeDiscovery.markInstallError(HOOK_SEND_THREADED_INJECT, error.message ?: error.javaClass.name)
                runCatching { Log.e(TAG, "threaded image inject method resolution failed", error) }
            }.getOrNull()
                .orEmpty()

        if (bindings.isEmpty()) {
            defaultBridgeDiscovery.markInstallError(HOOK_SEND_THREADED_INJECT, "no threaded inject hook candidate found")
            return
        }

        var anyInstalled = false
        bindings.forEach { binding ->
            runCatching {
                hookInstaller.hookBefore(binding.method) { invocation ->
                    val context = pendingContext.get() ?: return@hookBefore
                    val sendingLog = invocation.args.getOrNull(binding.sendingLogArgIndex) ?: return@hookBefore
                    injectThreadMetadata(TAG, binding, sendingLog, context)
                }
                anyInstalled = true
            }.onFailure { error ->
                runCatching { Log.e(TAG, "threaded image inject hook install failed source=${binding.source}", error) }
            }
        }

        if (anyInstalled) {
            defaultBridgeDiscovery.markInstalled(HOOK_SEND_THREADED_INJECT)
        } else {
            defaultBridgeDiscovery.markInstallError(HOOK_SEND_THREADED_INJECT, "threaded inject hook install failed")
        }
    }

    fun <T> withThreadContext(
        roomId: Long,
        threadId: Long,
        threadScope: Int,
        block: () -> T,
    ): T {
        pendingContext.set(ThreadedImagePendingContext(roomId, threadId, threadScope))
        return try {
            block()
        } finally {
            pendingContext.remove()
        }
    }
}

private fun injectThreadMetadata(
    tag: String,
    binding: ThreadedImageXposedInjector.InjectHookBinding,
    sendingLog: Any,
    context: ThreadedImagePendingContext,
) {
    runCatching {
        ReplyMarkdownSendingLogAccess.writeThreadMetadata(
            sendingLog = sendingLog,
            threadId = context.threadId,
            threadScope = context.threadScope,
        )
        defaultBridgeDiscovery.recordHook(
            HOOK_SEND_THREADED_INJECT,
            "source=${binding.source} room=${context.roomId} threadId=${context.threadId} scope=${context.threadScope}",
        )
    }.onFailure { error ->
        Log.e(tag, "thread metadata injection failed room=${context.roomId} source=${binding.source}", error)
    }
}
