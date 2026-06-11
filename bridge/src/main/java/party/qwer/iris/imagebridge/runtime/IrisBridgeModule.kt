package party.qwer.iris.imagebridge.runtime

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreRuntime
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import party.qwer.iris.imagebridge.runtime.discovery.defaultBridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTarget
import party.qwer.iris.imagebridge.runtime.kakao.KakaoTalkTargetContext
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.server.defaultImageBridgeServer
import party.qwer.iris.resolveBridgeToken
import java.lang.reflect.Method

private const val IRIS_BRIDGE_TAG = "IrisBridge"

class IrisBridgeModule : XposedModule() {
    companion object {
        private const val TAG = IRIS_BRIDGE_TAG
        private val SUPPORTED_PACKAGES = KakaoTalkTarget.SUPPORTED_PACKAGES
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
        if (param.packageName !in SUPPORTED_PACKAGES) return
        val target = KakaoTalkTarget.resolve(param.packageName)
        Log.i(TAG, "loaded into ${target.packageName}, hooking Application.onCreate")
        val onCreate = Application::class.java.getDeclaredMethod("onCreate")
        hookInstaller.hookAfter(onCreate) { invocation ->
            startBridge(invocation.thisObject as Application, param.classLoader, target)
        }
    }

    private fun startBridge(
        app: Application,
        classLoader: ClassLoader,
        target: KakaoTalkTargetContext,
    ) {
        Log.i(TAG, "Application.onCreate — starting image bridge server for ${target.packageName}")
        val bridgeCore = loadBridgeCore()
        val discovery = discoverKakaoClassRegistryForBridge { KakaoClassRegistry.discover(classLoader, target) }
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
            bridgeCore,
            kakaoClassLoader = classLoader,
        )
    }

    private fun loadBridgeCore(): BridgeCoreRuntime? =
        BridgeCore.loadOrNull(
            securityMode = System.getenv("IRIS_BRIDGE_SECURITY_MODE"),
            bridgeToken = resolveBridgeToken(),
            requireHandshakeRaw = System.getenv("IRIS_BRIDGE_REQUIRE_HANDSHAKE"),
        )
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
