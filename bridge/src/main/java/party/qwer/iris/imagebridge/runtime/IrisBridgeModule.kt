package party.qwer.iris.imagebridge.runtime

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.server.defaultImageBridgeServer
import java.lang.reflect.Method

private const val IRIS_BRIDGE_TAG = "IrisBridge"

class IrisBridgeModule : XposedModule() {
    companion object {
        private const val TAG = IRIS_BRIDGE_TAG
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
        runCatching {
            System.loadLibrary("iris_bridge_core")
            Log.i(TAG, "bridge-core abi=${BridgeCore.nativeAbiVersion()} loaded")
        }.onFailure { Log.e(TAG, "bridge-core load failed", it) }
        val discovery = discoverKakaoClassRegistryForBridge { KakaoClassRegistry.discover(classLoader) }
        discovery.registry?.let { defaultBridgeDiscovery.install(it, hookInstaller) }
        markdownHooks.install(classLoader, discovery.registry, hookInstaller)
        defaultImageBridgeServer.start(
            app,
            discovery.registry,
            discovery.errorMessage,
            mentionPendingContexts,
            leveragePendingContexts,
            leverageCommitPendingContexts,
            hookInstaller,
        )
    }
}

private data class KakaoClassRegistryDiscoveryResult(
    val registry: KakaoClassRegistry?,
    val errorMessage: String?,
)

private fun discoverKakaoClassRegistryForBridge(discover: () -> KakaoClassRegistry): KakaoClassRegistryDiscoveryResult {
    val registry =
        runCatching { discover() }
            .onFailure { error ->
                Log.w(IRIS_BRIDGE_TAG, "KakaoClassRegistry discovery failed", error)
            }
    return KakaoClassRegistryDiscoveryResult(
        registry = registry.getOrNull(),
        errorMessage = registry.exceptionOrNull()?.message,
    )
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
