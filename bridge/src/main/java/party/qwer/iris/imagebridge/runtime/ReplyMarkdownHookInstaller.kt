package party.qwer.iris.imagebridge.runtime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscovery
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_LEVERAGE_COMMIT
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_INGRESS
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_REQUEST
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_REPLY_MARKDOWN_REUSE
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownIngressCapture
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionIngressCapture
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import party.qwer.iris.imagebridge.runtime.send.selectConnectWriteType
import party.qwer.iris.imagebridge.runtime.send.selectLeverageMessageType
import java.util.concurrent.atomic.AtomicBoolean

private const val MARKDOWN_SHARE_ACTIVITY = "com.kakao.talk.activity.RecentExcludeIntentFilterActivity"
private const val MARKDOWN_REQUEST_COMPANION = "com.kakao.talk.manager.send.ChatSendingLogRequest\$a"

internal class ReplyMarkdownHookInstaller(
    private val tag: String,
    private val markdownPendingContexts: ReplyMarkdownPendingContextStore,
    private val mentionPendingContexts: ReplyMentionPendingContextStore,
    private val leveragePendingContexts: ReplyLeveragePendingContextStore,
    private val leverageCommitPendingContexts: ReplyLeveragePendingContextStore,
) {
    private val installed = AtomicBoolean(false)

    fun install(
        classLoader: ClassLoader,
        registry: KakaoClassRegistry?,
        hookInstaller: BridgeHookInstaller,
    ) {
        if (!installed.compareAndSet(false, true)) return
        installIngressHook(classLoader, hookInstaller)
        installReuseHook(hookInstaller)
        installRequestHook(classLoader, registry, hookInstaller)
        installLeverageCommitHook(classLoader, hookInstaller)
    }

    private fun installIngressHook(
        classLoader: ClassLoader,
        hookInstaller: BridgeHookInstaller,
    ) {
        try {
            val activityClass = Class.forName(MARKDOWN_SHARE_ACTIVITY, false, classLoader)
            val onCreateMethod = activityClass.getMethod("onCreate", Bundle::class.java)
            installReplyMarkdownIngressMethodHook(onCreateMethod, hookInstaller) { activity ->
                rememberReplyMarkdownIntent(activity.intent, HOOK_REPLY_MARKDOWN_INGRESS, mentionPendingContexts, markdownPendingContexts)
            }
            BridgeDiscovery.markInstalled(HOOK_REPLY_MARKDOWN_INGRESS)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_MARKDOWN_INGRESS, error.message ?: error.javaClass.name)
            Log.e(tag, "reply-markdown ingress hook install failed", error)
        }
    }

    private fun installReuseHook(hookInstaller: BridgeHookInstaller) {
        try {
            val onNewIntentMethod = Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }
            installReplyMarkdownReuseMethodHook(onNewIntentMethod, hookInstaller, MARKDOWN_SHARE_ACTIVITY) { intent ->
                rememberReplyMarkdownIntent(intent, HOOK_REPLY_MARKDOWN_REUSE, mentionPendingContexts, markdownPendingContexts)
            }
            BridgeDiscovery.markInstalled(HOOK_REPLY_MARKDOWN_REUSE)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_MARKDOWN_REUSE, error.message ?: error.javaClass.name)
            Log.e(tag, "reply-markdown reuse hook install failed", error)
        }
    }

    private fun installRequestHook(
        classLoader: ClassLoader,
        registry: KakaoClassRegistry?,
        hookInstaller: BridgeHookInstaller,
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
            injectMethods.forEach { injectMethod ->
                installReplyMarkdownRequestDispatchHook(
                    injectMethod,
                    hookInstaller,
                    tag,
                    markdownPendingContexts,
                    mentionPendingContexts,
                    leveragePendingContexts,
                    registry?.let(::selectLeverageMessageType),
                    registry?.let(::selectConnectWriteType),
                )
            }
            Log.i(tag, "reply-markdown request hooks installed: ${injectMethods.joinToString { it.name }}")
            BridgeDiscovery.markInstalled(HOOK_REPLY_MARKDOWN_REQUEST)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_MARKDOWN_REQUEST, error.message ?: error.javaClass.name)
            Log.e(tag, "reply-markdown request hook install failed", error)
        }
    }

    private fun installLeverageCommitHook(
        classLoader: ClassLoader,
        hookInstaller: BridgeHookInstaller,
    ) {
        try {
            val commitMethods =
                selectReplyLeverageChatLogCommitHookMethods(classLoader)
                    .ifEmpty { error("reply leverage chatLog commit hook target not found") }
            commitMethods.forEach { commitMethod ->
                installReplyLeverageChatLogCommitHook(
                    commitMethod,
                    hookInstaller,
                    tag,
                    leverageCommitPendingContexts,
                )
            }
            Log.i(tag, "reply leverage chatlog commit hooks installed: ${commitMethods.joinToString { it.name }}")
            BridgeDiscovery.markInstalled(HOOK_REPLY_LEVERAGE_COMMIT)
        } catch (error: Throwable) {
            BridgeDiscovery.markInstallError(HOOK_REPLY_LEVERAGE_COMMIT, error.message ?: error.javaClass.name)
            Log.e(tag, "reply leverage chatlog commit hook install failed", error)
        }
    }
}

private fun rememberReplyMarkdownIntent(
    intent: Intent?,
    hookName: String,
    mentionPendingContexts: ReplyMentionPendingContextStore,
    markdownPendingContexts: ReplyMarkdownPendingContextStore,
) {
    ReplyMentionIngressCapture.capture(intent, mentionPendingContexts) { context ->
        BridgeDiscovery.recordHook(hookName, "room=${context.roomId} mention=true text=${context.messageText.take(32)}")
    }
    ReplyMarkdownIngressCapture.capture(intent, markdownPendingContexts) { context ->
        BridgeDiscovery.recordHook(hookName, "room=${context.roomId} scope=${context.threadScope} text=${context.messageText.take(32)}")
    }
}
