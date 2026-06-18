package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject

internal object BridgeCoreJniPolicy {
    fun nativeIsTruthyFlag(raw: String): Boolean = BridgeCoreJniDispatcher.booleanValue("policy.isTruthyFlag", JSONObject().put("raw", raw))

    fun nativeNormalizeSecurityMode(raw: String?): String =
        BridgeCoreJniDispatcher.stringValue(
            "policy.normalizeSecurityMode",
            JSONObject().putNullable("raw", raw),
        )

    fun nativeAllowedPeerUids(
        securityModeRaw: String?,
        extraUidsRaw: String?,
    ): IntArray =
        BridgeCoreJniDispatcher.intArrayValue(
            "policy.allowedPeerUids",
            JSONObject()
                .putNullable("securityModeRaw", securityModeRaw)
                .putNullable("extraUidsRaw", extraUidsRaw),
        )

    fun nativeSendBlockReason(
        installAttempted: Boolean,
        hookNames: Array<String>,
        hookInstalled: BooleanArray,
        imageCount: Int,
        threadId: Long,
        hasThreadId: Boolean,
        threadScope: Int,
        hasThreadScope: Boolean,
    ): String =
        BridgeCoreJniDispatcher.stringValue(
            "policy.sendBlockReason",
            JSONObject()
                .put("installAttempted", installAttempted)
                .put("hookNames", JSONArray().putAll(hookNames))
                .put("hookInstalled", JSONArray().putAll(hookInstalled))
                .put("imageCount", imageCount)
                .put("threadId", if (hasThreadId) threadId else JSONObject.NULL)
                .put("threadScope", if (hasThreadScope) threadScope else JSONObject.NULL),
        )

    fun nativeCurrentBridgeCapabilities(
        registryAvailable: Boolean,
        registryError: String?,
        specReady: Boolean,
        notificationActionSupported: Boolean,
        textSupported: Boolean,
        textReady: Boolean,
        textReason: String?,
        sendTextEnabled: Boolean,
        sendMarkdownEnabled: Boolean,
    ): String =
        BridgeCoreJniDispatcher.envelope(
            "policy.currentBridgeCapabilities",
            JSONObject()
                .put("registryAvailable", registryAvailable)
                .putNullable("registryError", registryError)
                .put("specReady", specReady)
                .put("notificationActionSupported", notificationActionSupported)
                .put("textSupported", textSupported)
                .put("textReady", textReady)
                .putNullable("textReason", textReason)
                .put("sendTextEnabled", sendTextEnabled)
                .put("sendMarkdownEnabled", sendMarkdownEnabled),
        )

    fun nativeServerRestartDelayMs(failureCount: Int): Long =
        BridgeCoreJniDispatcher.longValue(
            "policy.serverRestartDelayMs",
            JSONObject().put("failureCount", failureCount),
            default = 1_000L,
        )
}
