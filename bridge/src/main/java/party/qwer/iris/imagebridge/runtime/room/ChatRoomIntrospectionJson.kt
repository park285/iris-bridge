package party.qwer.iris.imagebridge.runtime.room

import org.json.JSONArray
import org.json.JSONObject

internal fun ChatRoomIntrospector.ScanResult.toJson(): JSONObject =
    JSONObject().apply {
        put("className", className)
        put("scannedAt", scannedAt)
        put("fields", JSONArray(fields.map { it.toJson() }))
    }

private fun ChatRoomIntrospector.FieldInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("name", name)
        put("type", type)
        value?.let { put("value", it) }
        size?.let { put("size", it) }
        elementType?.let { put("elementType", it) }
        if (nested.isNotEmpty()) {
            put("nested", JSONArray(nested.map { it.toJson() }))
        }
        if (elements.isNotEmpty()) {
            put("elements", JSONArray(elements.map { it.toJson() }))
        }
    }
