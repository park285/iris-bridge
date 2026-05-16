package party.qwer.iris.imagebridge.runtime

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import java.lang.reflect.Method

class IrisBridgeModule : XposedModule() {
    companion object {
        private const val TAG = "IrisBridge"
        private const val TARGET_PACKAGE = "com.kakao.talk"
        private val markdownPendingContexts = ReplyMarkdownPendingContextStore()
        private val mentionPendingContexts = ReplyMentionPendingContextStore()
        private val leveragePendingContexts = ReplyLeveragePendingContextStore()
        private val leverageCommitPendingContexts = ReplyLeveragePendingContextStore()
        private val markdownHooks =
            ReplyMarkdownHookInstaller(
                tag = TAG,
                markdownPendingContexts = markdownPendingContexts,
                mentionPendingContexts = mentionPendingContexts,
                leveragePendingContexts = leveragePendingContexts,
                leverageCommitPendingContexts = leverageCommitPendingContexts,
            )
    }

    private val hookInstaller = ModernBridgeHookInstaller(this)

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return
        Log.i(TAG, "loaded into $TARGET_PACKAGE, hooking Application.onCreate")
        val onCreate = Application::class.java.getDeclaredMethod("onCreate")
        hookInstaller.hookAfter(onCreate) { invocation ->
            startBridge(invocation.thisObject as Application, param.classLoader)
        }
    }

    private fun startBridge(
        app: Application,
        classLoader: ClassLoader,
    ) {
        Log.i(TAG, "Application.onCreate — starting image bridge server")
        val registry = runCatching { KakaoClassRegistry.discover(classLoader) }
        registry.getOrNull()?.let { BridgeDiscovery.install(it, hookInstaller) }
        markdownHooks.install(classLoader, registry.getOrNull(), hookInstaller)
        ImageBridgeServer.start(
            app,
            registry.getOrNull(),
            registry.exceptionOrNull()?.message,
            mentionPendingContexts,
            leveragePendingContexts,
            leverageCommitPendingContexts,
            hookInstaller,
        )
    }
}

private class ModernBridgeHookInstaller(
    private val module: XposedModule,
) : BridgeHookInstaller {
    override fun hookBefore(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        module
            .hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                callback(chain.toBridgeInvocation())
                chain.proceed()
            }
    }

    override fun hookAfter(
        method: Method,
        callback: (BridgeHookInvocation) -> Unit,
    ) {
        module
            .hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val invocation = chain.toBridgeInvocation()
                val result = chain.proceed()
                callback(invocation)
                result
            }
    }

    private fun XposedInterface.Chain.toBridgeInvocation(): BridgeHookInvocation =
        BridgeHookInvocation(
            thisObject = thisObject,
            args = args.toTypedArray(),
        )
}
