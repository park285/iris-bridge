package party.qwer.iris.imagebridge.runtime

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_INGRESS
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_REQUEST
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_REUSE
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownIngressCapture
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownSendingLogAccess
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionIngressCapture
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionSendingLogAccess
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeServer
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class IrisBridgeModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "IrisBridge"
        private const val TARGET_PACKAGE = "com.kakao.talk"
        private const val MARKDOWN_SHARE_ACTIVITY = "com.kakao.talk.activity.RecentExcludeIntentFilterActivity"
        private const val MARKDOWN_REQUEST_COMPANION = "com.kakao.talk.manager.send.ChatSendingLogRequest\$a"

        private val markdownHooksInstalled = AtomicBoolean(false)
        private val markdownPendingContexts = ReplyMarkdownPendingContextStore()
        private val mentionPendingContexts = ReplyMentionPendingContextStore()
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        Log.i(TAG, "loaded into $TARGET_PACKAGE, hooking Application.onCreate")

        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as Application
                    Log.i(TAG, "Application.onCreate — starting image bridge server")
                    val registry = runCatching { KakaoClassRegistry.discover(lpparam.classLoader) }
                    registry.getOrNull()?.let { BridgeDiscovery.install(it) }
                    installReplyMarkdownHooks(lpparam.classLoader, registry.getOrNull())
                    ImageBridgeServer.start(app, registry.getOrNull(), registry.exceptionOrNull()?.message)
                }
            },
        )
    }

    private fun installReplyMarkdownHooks(
        classLoader: ClassLoader,
        registry: KakaoClassRegistry?,
    ) {
        if (!markdownHooksInstalled.compareAndSet(false, true)) {
            return
        }
        installReplyMarkdownIngressHook(classLoader)
        installReplyMarkdownReuseHook()
        installReplyMarkdownRequestHook(classLoader, registry)
    }

    private fun installReplyMarkdownIngressHook(classLoader: ClassLoader) {
        try {
            val activityClass = Class.forName(MARKDOWN_SHARE_ACTIVITY, false, classLoader)
            val onCreateMethod = activityClass.getMethod("onCreate", Bundle::class.java)
            XposedBridge.hookMethod(
                onCreateMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        rememberReplyMarkdownIntent(activity.intent, HOOK_REPLY_MARKDOWN_INGRESS)
                    }
                },
            )
            BridgeDiscovery.markInstalled(HOOK_REPLY_MARKDOWN_INGRESS)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_MARKDOWN_INGRESS, error.message ?: error.javaClass.name)
            Log.e(TAG, "reply-markdown ingress hook install failed", error)
        }
    }

    private fun installReplyMarkdownReuseHook() {
        try {
            val onNewIntentMethod = Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }
            XposedBridge.hookMethod(
                onNewIntentMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (activity.javaClass.name != MARKDOWN_SHARE_ACTIVITY) return
                        rememberReplyMarkdownIntent(param.args.getOrNull(0) as? Intent, HOOK_REPLY_MARKDOWN_REUSE)
                    }
                },
            )
            BridgeDiscovery.markInstalled(HOOK_REPLY_MARKDOWN_REUSE)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_MARKDOWN_REUSE, error.message ?: error.javaClass.name)
            Log.e(TAG, "reply-markdown reuse hook install failed", error)
        }
    }

    private fun installReplyMarkdownRequestHook(
        classLoader: ClassLoader,
        registry: KakaoClassRegistry?,
    ) {
        try {
            val companionClass = Class.forName(MARKDOWN_REQUEST_COMPANION, false, classLoader)
            val injectMethods =
                selectReplyMarkdownRequestHookMethods(
                    companionClass,
                    registry?.chatRoomClass,
                    registry?.writeTypeClass,
                    registry?.listenerClass,
                ).ifEmpty { error("reply-markdown request hook target not found") }
            val hook =
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val sendingLog = param.args.getOrNull(1) ?: return
                        val roomId = ReplyMarkdownSendingLogAccess.readRoomId(sendingLog) ?: return
                        val messageText = ReplyMarkdownSendingLogAccess.readMessageText(sendingLog) ?: return
                        val currentSessionId = ReplyMarkdownSendingLogAccess.readAttachmentSessionId(sendingLog)
                        val mentionContext = mentionPendingContexts.match(roomId, messageText, currentSessionId)
                        val mentionInjected =
                            runCatching {
                                ReplyMentionSendingLogAccess.injectMentionAttachment(
                                    sendingLog,
                                    mentionContext?.attachmentText,
                                )
                            }.onFailure { error ->
                                Log.e(TAG, "reply mention attachment injection failed room=$roomId", error)
                            }.getOrDefault(false)
                        val sessionId = ReplyMarkdownSendingLogAccess.readAttachmentSessionId(sendingLog)
                        val context =
                            markdownPendingContexts.match(roomId, messageText, sessionId)
                                ?: run {
                                    if (mentionInjected) {
                                        BridgeDiscovery.recordHook(
                                            HOOK_REPLY_MARKDOWN_REQUEST,
                                            "room=$roomId mention=true",
                                        )
                                    }
                                    return
                                }
                        runCatching {
                            ReplyMarkdownSendingLogAccess.writeThreadMetadata(
                                sendingLog = sendingLog,
                                threadId = context.threadId,
                                threadScope = context.threadScope,
                            )
                            BridgeDiscovery.recordHook(
                                HOOK_REPLY_MARKDOWN_REQUEST,
                                "room=${context.roomId} threadId=${context.threadId} scope=${context.threadScope} mention=$mentionInjected",
                            )
                        }.onFailure { error ->
                            Log.e(TAG, "reply-markdown request injection failed room=${context.roomId}", error)
                        }
                    }
                }
            injectMethods.forEach { injectMethod ->
                XposedBridge.hookMethod(injectMethod, hook)
            }
            Log.i(TAG, "reply-markdown request hooks installed: ${injectMethods.joinToString { it.name }}")
            BridgeDiscovery.markInstalled(HOOK_REPLY_MARKDOWN_REQUEST)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_MARKDOWN_REQUEST, error.message ?: error.javaClass.name)
            Log.e(TAG, "reply-markdown request hook install failed", error)
        }
    }

    private fun rememberReplyMarkdownIntent(
        intent: Intent?,
        hookName: String,
    ) {
        ReplyMentionIngressCapture.capture(intent, mentionPendingContexts) { context ->
            BridgeDiscovery.recordHook(
                hookName,
                "room=${context.roomId} mention=true text=${context.messageText.take(32)}",
            )
        }
        ReplyMarkdownIngressCapture.capture(intent, markdownPendingContexts) { context ->
            BridgeDiscovery.recordHook(
                hookName,
                "room=${context.roomId} scope=${context.threadScope} text=${context.messageText.take(32)}",
            )
        }
    }
}

internal fun selectReplyMarkdownRequestHookMethodForTest(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): Method? = selectReplyMarkdownRequestHookMethod(companionClass, chatRoomClass, writeTypeClass, listenerClass)

internal fun selectReplyMarkdownRequestHookMethodsForTest(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): List<Method> = selectReplyMarkdownRequestHookMethods(companionClass, chatRoomClass, writeTypeClass, listenerClass)

private fun selectReplyMarkdownRequestHookMethod(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): Method? = selectReplyMarkdownRequestHookMethods(companionClass, chatRoomClass, writeTypeClass, listenerClass).firstOrNull()

private fun selectReplyMarkdownRequestHookMethods(
    companionClass: Class<*>,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): List<Method> =
    companionClass.methods
        .asSequence()
        .filter { method ->
            method.parameterCount == 5 &&
                method.parameterTypes[4] == Boolean::class.javaPrimitiveType &&
                matchesReplyMarkdownRequestTypes(method, chatRoomClass, writeTypeClass, listenerClass)
        }.sortedWith(
            compareBy<Method> { if (it.name == "u") 0 else 1 }
                .thenBy { it.name }
                .thenBy { it.toGenericString() },
        ).toList()

private fun matchesReplyMarkdownRequestTypes(
    method: Method,
    chatRoomClass: Class<*>?,
    writeTypeClass: Class<*>?,
    listenerClass: Class<*>?,
): Boolean {
    if (chatRoomClass == null || writeTypeClass == null || listenerClass == null) {
        return method.name == "u"
    }
    val parameterTypes = method.parameterTypes
    return parameterTypes[0].isAssignableFrom(chatRoomClass) &&
        parameterTypes[2].isAssignableFrom(writeTypeClass) &&
        parameterTypes[3].isAssignableFrom(listenerClass)
}
