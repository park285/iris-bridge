package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

sealed interface DedupeState {
    data object Fresh : DedupeState

    data object InFlight : DedupeState

    data class Cached(
        val responseJson: String?,
    ) : DedupeState
}

class BridgeCoreEnvelope private constructor(
    private val json: JSONObject,
) {
    val isOk: Boolean
        get() = json.optBoolean("ok", false)

    val errorCode: String?
        get() = json.optString("errorCode").takeIf { it.isNotEmpty() }

    val errorMessage: String?
        get() = json.optString("error").takeIf { it.isNotEmpty() }

    fun string(key: String): String? = if (json.has(key) && !json.isNull(key)) json.optString(key) else null

    fun bool(key: String): Boolean? = if (json.has(key) && !json.isNull(key)) json.optBoolean(key) else null

    fun strictBool(key: String): Boolean? = if (json.has(key) && !json.isNull(key)) json.opt(key) as? Boolean else null

    fun long(key: String): Long? = if (json.has(key) && !json.isNull(key)) json.optLong(key) else null

    fun int(key: String): Int? = if (json.has(key) && !json.isNull(key)) json.optInt(key) else null

    fun intList(key: String): List<Int>? {
        val array = if (json.has(key) && !json.isNull(key)) json.optJSONArray(key) else null
        return array?.let { values -> List(values.length()) { index -> values.optInt(index) } }
    }

    fun stringList(key: String): List<String>? {
        val array = if (json.has(key) && !json.isNull(key)) json.optJSONArray(key) else null
        return array?.let { values -> List(values.length()) { index -> values.optString(index) } }
    }

    fun dedupeState(): DedupeState? =
        when (string("state")) {
            "fresh" -> DedupeState.Fresh
            "inFlight" -> DedupeState.InFlight
            "cached" -> DedupeState.Cached(string("responseJson"))
            else -> null
        }

    companion object {
        fun parse(raw: String): BridgeCoreEnvelope = BridgeCoreEnvelope(JSONObject(raw))
    }
}
