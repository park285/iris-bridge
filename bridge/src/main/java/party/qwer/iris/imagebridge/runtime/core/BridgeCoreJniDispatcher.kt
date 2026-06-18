package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject

internal object BridgeCoreJniDispatcher {
    private external fun nativeDispatch(
        op: String,
        payload: String,
    ): String

    fun envelope(
        op: String,
        payload: JSONObject = JSONObject(),
    ): String = nativeDispatch(op, payload.toString())

    fun parsed(
        op: String,
        payload: JSONObject = JSONObject(),
    ): BridgeCoreEnvelope = BridgeCoreEnvelope.parse(envelope(op, payload))

    fun booleanValue(
        op: String,
        payload: JSONObject = JSONObject(),
        default: Boolean = false,
    ): Boolean = parsed(op, payload).strictBool("value") ?: default

    fun intValue(
        op: String,
        payload: JSONObject = JSONObject(),
        default: Int = 0,
    ): Int = parsed(op, payload).int("value") ?: default

    fun longValue(
        op: String,
        payload: JSONObject = JSONObject(),
        default: Long = 0L,
    ): Long = parsed(op, payload).long("value") ?: default

    fun stringValue(
        op: String,
        payload: JSONObject = JSONObject(),
        default: String = "",
    ): String = parsed(op, payload).string("value") ?: default

    fun optionalStringValue(
        op: String,
        payload: JSONObject = JSONObject(),
    ): String? = parsed(op, payload).string("value")

    fun intArrayValue(
        op: String,
        payload: JSONObject = JSONObject(),
    ): IntArray = parsed(op, payload).intList("value")?.toIntArray() ?: IntArray(0)
}

internal fun JSONObject.putNullable(
    key: String,
    value: String?,
): JSONObject = put(key, value ?: JSONObject.NULL)

internal fun JSONArray.putAll(values: Array<String>): JSONArray =
    apply {
        values.forEach(::put)
    }

internal fun JSONArray.putAll(values: BooleanArray): JSONArray =
    apply {
        values.forEach(::put)
    }
