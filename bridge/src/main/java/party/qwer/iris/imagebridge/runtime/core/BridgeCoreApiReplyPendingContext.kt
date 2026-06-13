package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal fun BridgeCore.replyMarkdownPendingContextJson(
    requestJson: String,
    loadCompatibleCore: () -> Boolean = ::bridgeCoreLoadCompatibleLibraryOnce,
    nativePendingContext: (String) -> String = BridgeCoreJniReply::nativeReplyMarkdownPendingContext,
): JSONObject? = replyPendingContextJson(requestJson, loadCompatibleCore, nativePendingContext)

internal fun BridgeCore.replyMentionPendingContextJson(
    requestJson: String,
    loadCompatibleCore: () -> Boolean = ::bridgeCoreLoadCompatibleLibraryOnce,
    nativePendingContext: (String) -> String = BridgeCoreJniReply::nativeReplyMentionPendingContext,
): JSONObject? = replyPendingContextJson(requestJson, loadCompatibleCore, nativePendingContext)

private fun replyPendingContextJson(
    requestJson: String,
    loadCompatibleCore: () -> Boolean,
    nativePendingContext: (String) -> String,
): JSONObject? {
    if (!loadCompatibleCore()) {
        return null
    }
    val envelope =
        runCatching {
            JSONObject(nativePendingContext(requestJson))
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core reply pending context policy threw", error)
            return null
        }
    if (!envelope.optBoolean("ok", false)) {
        throw IllegalArgumentException(
            envelope.optString("error").takeIf { it.isNotEmpty() }
                ?: "bridge core rejected reply pending context request",
        )
    }
    return envelope.optJSONObject("context")
}
