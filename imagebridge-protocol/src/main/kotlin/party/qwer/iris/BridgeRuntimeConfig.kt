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
private data class BridgeRuntimeConfigSnapshot(
    val bridgeToken: String = "",
    val replyImageDir: String = "",
    val bridgeMuxServerEnabled: Boolean? = null,
    val textBridgeSendTextEnabled: Boolean? = null,
    val textBridgeSendMarkdownEnabled: Boolean? = null,
)

enum class BridgeTokenSource {
    CONFIG_FILE,
    ENV_FALLBACK,
    NONE,
}

data class BridgeTokenResolution(
    val token: String,
    val source: BridgeTokenSource,
    val configPath: String,
    val replyImageDir: String,
    val bridgeMuxServerEnabled: Boolean,
    val textBridgeSendTextEnabled: Boolean,
    val textBridgeSendMarkdownEnabled: Boolean,
)

object BridgeBootstrapConfigResolver {
    fun resolve(
        env: Map<String, String> = System.getenv(),
        fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
    ): BridgeTokenResolution {
        val configPath = IrisRuntimePathPolicy.resolve(env).configPath
        val paths = IrisRuntimePathPolicy.resolve(env)
        val envToken =
            env["IRIS_BRIDGE_TOKEN"]
                ?.trim()
                .orEmpty()
        val configSnapshot =
            runCatching {
                fileReader(configPath)
            }.getOrNull()
                ?.let(::decodeBridgeRuntimeConfig)
        val configToken = configSnapshot?.bridgeToken?.trim().orEmpty()
        val replyImageDir =
            configSnapshot
                ?.replyImageDir
                ?.trim()
                .orEmpty()
                .ifBlank { paths.replyImageDir }
        val bridgeMuxServerEnabled =
            resolveBooleanFlag(
                env = env,
                envKey = "IRIS_BRIDGE_MUX_SERVER_ENABLED",
                configValue = configSnapshot?.bridgeMuxServerEnabled,
            )
        val textBridgeSendTextEnabled =
            resolveBooleanFlag(
                env = env,
                envKey = "IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED",
                configValue = configSnapshot?.textBridgeSendTextEnabled,
            )
        val textBridgeSendMarkdownEnabled =
            resolveBooleanFlag(
                env = env,
                envKey = "IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED",
                configValue = configSnapshot?.textBridgeSendMarkdownEnabled,
            )
        return when {
            configToken.isNotBlank() ->
                BridgeTokenResolution(
                    token = configToken,
                    source = BridgeTokenSource.CONFIG_FILE,
                    configPath = configPath,
                    replyImageDir = replyImageDir,
                    bridgeMuxServerEnabled = bridgeMuxServerEnabled,
                    textBridgeSendTextEnabled = textBridgeSendTextEnabled,
                    textBridgeSendMarkdownEnabled = textBridgeSendMarkdownEnabled,
                )

            envToken.isNotBlank() ->
                BridgeTokenResolution(
                    token = envToken,
                    source = BridgeTokenSource.ENV_FALLBACK,
                    configPath = configPath,
                    replyImageDir = replyImageDir,
                    bridgeMuxServerEnabled = bridgeMuxServerEnabled,
                    textBridgeSendTextEnabled = textBridgeSendTextEnabled,
                    textBridgeSendMarkdownEnabled = textBridgeSendMarkdownEnabled,
                )

            else ->
                BridgeTokenResolution(
                    token = "",
                    source = BridgeTokenSource.NONE,
                    configPath = configPath,
                    replyImageDir = replyImageDir,
                    bridgeMuxServerEnabled = bridgeMuxServerEnabled,
                    textBridgeSendTextEnabled = textBridgeSendTextEnabled,
                    textBridgeSendMarkdownEnabled = textBridgeSendMarkdownEnabled,
                )
        }
    }
}

fun resolveBridgeToken(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): String = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).token

fun resolveBridgeReplyImageDir(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): String = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).replyImageDir

fun resolveBridgeMuxServerEnabled(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): Boolean = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).bridgeMuxServerEnabled

fun resolveBridgeTextSendTextEnabled(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): Boolean = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).textBridgeSendTextEnabled

fun resolveBridgeTextSendMarkdownEnabled(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): Boolean = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).textBridgeSendMarkdownEnabled

internal fun decodeBridgeToken(rawConfig: String): String =
    decodeBridgeRuntimeConfig(rawConfig)
        ?.bridgeToken
        ?.trim()
        .orEmpty()

private fun resolveBooleanFlag(
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

private fun decodeBridgeRuntimeConfig(rawConfig: String): BridgeRuntimeConfigSnapshot? =
    runCatching {
        bridgeRuntimeConfigJson.decodeFromString<BridgeRuntimeConfigSnapshot>(rawConfig)
    }.getOrNull()

private fun readBridgeRuntimeConfigFile(path: String): String? =
    runCatching {
        File(path)
            .takeIf { it.isFile }
            ?.readText()
    }.getOrNull()
