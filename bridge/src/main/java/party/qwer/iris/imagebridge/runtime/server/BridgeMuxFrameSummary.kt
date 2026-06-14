package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONObject
import party.qwer.iris.ImageBridgeMuxFrame

internal fun ImageBridgeMuxFrame.muxSummaryJson(): String =
    buildString {
        append("{\"type\":")
        append(JSONObject.quote(type))
        append(",\"muxVersion\":")
        append(muxVersion)
        correlationId?.let { value ->
            append(",\"correlationId\":")
            append(JSONObject.quote(value))
        }
        if (request != null) append(",\"request\":{}")
        if (response != null) append(",\"response\":{}")
        errorCode?.let { value ->
            append(",\"errorCode\":")
            append(JSONObject.quote(value))
        }
        append('}')
    }
