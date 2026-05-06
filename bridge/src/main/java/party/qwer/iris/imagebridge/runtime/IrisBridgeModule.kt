package party.qwer.iris.imagebridge.runtime

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import java.lang.reflect.Method

class IrisBridgeModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "IrisBridge"
        private const val TARGET_PACKAGE = "com.kakao.talk"
        private val markdownPendingContexts = ReplyMarkdownPendingContextStore()
        private val mentionPendingContexts = ReplyMentionPendingContextStore()
        private val markdownHooks =
            ReplyMarkdownHookInstaller(
                tag = TAG,
                markdownPendingContexts = markdownPendingContexts,
                mentionPendingContexts = mentionPendingContexts,
            )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        Log.i(TAG, "loaded into $TARGET_PACKAGE, hooking Application.onCreate")
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    startBridge(param.thisObject as Application, lpparam.classLoader)
                }
            },
        )
    }

    private fun startBridge(
        app: Application,
        classLoader: ClassLoader,
    ) {
        Log.i(TAG, "Application.onCreate — starting image bridge server")
        val registry = runCatching { KakaoClassRegistry.discover(classLoader) }
        registry.getOrNull()?.let { BridgeDiscovery.install(it) }
        markdownHooks.install(classLoader, registry.getOrNull())
        ImageBridgeServer.start(
            app,
            registry.getOrNull(),
            registry.exceptionOrNull()?.message,
            mentionPendingContexts,
        )
    }
}

internal fun installReplyMarkdownIngressMethodHook(
    method: Method,
    onActivity: (Activity) -> Unit,
) {
    XposedBridge.hookMethod(
        method,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                onActivity(activity)
            }
        },
    )
}

internal fun installReplyMarkdownReuseMethodHook(
    method: Method,
    activityClassName: String,
    onIntent: (Intent?) -> Unit,
) {
    XposedBridge.hookMethod(
        method,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (activity.javaClass.name != activityClassName) return
                onIntent(param.args.getOrNull(0) as? Intent)
            }
        },
    )
}

internal fun installReplyMarkdownRequestDispatchHook(
    method: Method,
    tag: String,
    markdownPendingContexts: ReplyMarkdownPendingContextStore,
    mentionPendingContexts: ReplyMentionPendingContextStore,
) {
    XposedBridge.hookMethod(
        method,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                handleReplyMarkdownRequestArgs(
                    param.args,
                    tag,
                    markdownPendingContexts,
                    mentionPendingContexts,
                )
            }
        },
    )
}
