package party.qwer.iris.imagebridge.runtime

import android.app.Activity
import android.content.Intent
import party.qwer.iris.imagebridge.runtime.reply.ReplyLeveragePendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMarkdownPendingContextStore
import party.qwer.iris.imagebridge.runtime.reply.ReplyMentionPendingContextStore
import java.lang.reflect.Method

internal fun installReplyMarkdownIngressMethodHook(
    method: Method,
    hookInstaller: BridgeHookInstaller,
    onActivity: (Activity) -> Unit,
) {
    hookInstaller.hookBefore(method) { invocation ->
        val activity = invocation.thisObject as? Activity ?: return@hookBefore
        onActivity(activity)
    }
}

internal fun installReplyMarkdownReuseMethodHook(
    method: Method,
    hookInstaller: BridgeHookInstaller,
    activityClassName: String,
    onIntent: (Intent?) -> Unit,
) {
    hookInstaller.hookBefore(method) { invocation ->
        val activity = invocation.thisObject as? Activity ?: return@hookBefore
        if (activity.javaClass.name != activityClassName) return@hookBefore
        onIntent(invocation.args.getOrNull(0) as? Intent)
    }
}

internal fun installReplyMarkdownRequestDispatchHook(
    method: Method,
    hookInstaller: BridgeHookInstaller,
    tag: String,
    markdownPendingContexts: ReplyMarkdownPendingContextStore,
    mentionPendingContexts: ReplyMentionPendingContextStore,
    leveragePendingContexts: ReplyLeveragePendingContextStore,
    leverageMessageType: Any?,
    leverageWriteType: Any?,
) {
    hookInstaller.hookBefore(method) { invocation ->
        handleReplyMarkdownRequestArgs(
            invocation.args,
            tag,
            markdownPendingContexts,
            mentionPendingContexts,
            leveragePendingContexts,
            leverageMessageType,
            leverageWriteType,
        )
    }
}

internal fun installReplyLeverageChatLogCommitHook(
    method: Method,
    hookInstaller: BridgeHookInstaller,
    tag: String,
    leveragePendingContexts: ReplyLeveragePendingContextStore,
) {
    hookInstaller.hookBefore(method) { invocation ->
        handleReplyLeverageChatLogCommitArgs(
            request = invocation.thisObject,
            args = invocation.args,
            tag = tag,
            leveragePendingContexts = leveragePendingContexts,
        )
    }
}
