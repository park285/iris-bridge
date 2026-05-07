package party.qwer.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val bridgeRuntimeConfigJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@Serializable
internal data class BridgeRuntimeConfigSnapshot(
    val bridgeToken: String = "",
    val replyImageDir: String = "",
    val textBridgeSendTextEnabled: Boolean? = null,
    val textBridgeSendMarkdownEnabled: Boolean? = null,
)

internal fun decodeBridgeRuntimeConfig(rawConfig: String): BridgeRuntimeConfigSnapshot? =
    runCatching {
        bridgeRuntimeConfigJson.decodeFromString<BridgeRuntimeConfigSnapshot>(rawConfig)
    }.getOrNull()

internal fun readBridgeRuntimeConfigFile(path: String): String? =
    runCatching {
        File(path)
            .takeIf { it.isFile }
            ?.readText()
    }.getOrNull()

internal fun resolveBridgeBooleanFlag(
    env: Map<String, String>,
    envKey: String,
    configValue: Boolean?,
): Boolean =
    env[envKey]
        ?.let(::isTruthy)
        ?: (configValue != false)

private fun isTruthy(raw: String): Boolean =
    when (raw.trim().lowercase()) {
        "true", "1", "on", "yes" -> true
        else -> false
    }
