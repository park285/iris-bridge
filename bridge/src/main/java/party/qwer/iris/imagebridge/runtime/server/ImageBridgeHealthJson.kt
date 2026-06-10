package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONArray
import org.json.JSONObject

internal fun ImageBridgeHealthSnapshot.toJson(): JSONObject =
    JSONObject().apply {
        put("status", party.qwer.iris.ImageBridgeProtocol.STATUS_OK)
        put("running", running)
        put("specReady", specStatus.ready)
        put("checkedAtEpochMs", specStatus.checkedAtEpochMs)
        put("restartCount", restartCount)
        put("bridgeCoreUnavailable", bridgeCoreUnavailable)
        if (!lastCrashMessage.isNullOrBlank()) put("lastCrashMessage", lastCrashMessage)
        put("checks", JSONArray(specStatus.checks.map { it.toJson() }))
        put("discovery", discoverySnapshot.toJson())
        put("capabilities", capabilities.toJson())
        metrics?.let { put("metrics", it.toJson()) }
    }

private fun BridgeSpecCheck.toJson(): JSONObject =
    JSONObject().apply {
        put("name", name)
        put("ok", ok)
        if (!detail.isNullOrBlank()) put("detail", detail)
    }

private fun party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot.toJson(): JSONObject =
    JSONObject().apply {
        put("installAttempted", installAttempted)
        put(
            "hooks",
            JSONArray(
                hooks.map { hook ->
                    JSONObject().apply {
                        put("name", hook.name)
                        put("installed", hook.installed)
                        if (!hook.installError.isNullOrBlank()) put("installError", hook.installError)
                        put("invocationCount", hook.invocationCount)
                        hook.lastSeenEpochMs?.let { put("lastSeenEpochMs", it) }
                        if (!hook.lastSummary.isNullOrBlank()) put("lastSummary", hook.lastSummary)
                    }
                },
            ),
        )
    }

private fun ImageBridgeCapabilitiesSnapshot.toJson(): JSONObject =
    JSONObject().apply {
        put("inspectChatRoom", inspectChatRoom.toJson())
        put("openChatRoom", openChatRoom.toJson())
        put("snapshotChatRoomMembers", snapshotChatRoomMembers.toJson())
        put("sendText", sendText.toJson())
        put("sendMarkdown", sendMarkdown.toJson())
    }

private fun ImageBridgeCapabilitySnapshot.toJson(): JSONObject =
    JSONObject().apply {
        put("supported", supported)
        put("ready", ready)
        reason?.let { put("reason", it) }
    }

private fun party.qwer.iris.ImageBridgeProtocol.ImageBridgeMetrics.toJson(): JSONObject =
    JSONObject().apply {
        put("sendSuccess", sendSuccess)
        put("sendFailure", sendFailure)
        put("pathValidationFailure", pathValidationFailure)
        put("unauthorizedClient", unauthorizedClient)
        put("bridgeBusy", bridgeBusy)
        put("bridgeShuttingDown", bridgeShuttingDown)
        put("timeout", timeout)
        put("missingRequestId", missingRequestId)
        put("rejectedClient", rejectedClient)
        put("activeClient", activeClient)
        put("queuedClient", queuedClient)
        lastSendRequestId?.let { put("lastSendRequestId", it) }
        lastSendStartedAtEpochMs?.let { put("lastSendStartedAtEpochMs", it) }
        lastSendCompletedAtEpochMs?.let { put("lastSendCompletedAtEpochMs", it) }
        lastSendDurationMs?.let { put("lastSendDurationMs", it) }
        lastSendErrorCode?.let { put("lastSendErrorCode", it) }
    }
