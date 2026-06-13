package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONObject
import party.qwer.iris.ImageBridgeMuxFrame

internal fun ImageBridgeMuxFrame.muxSummaryJson(): String =
    JSONObject()
        .apply {
            put("type", type)
            put("muxVersion", muxVersion)
            correlationId?.let { put("correlationId", it) }
            if (request != null) put("request", JSONObject())
            if (response != null) put("response", JSONObject())
            errorCode?.let { put("errorCode", it) }
        }.toString()
