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
        return when {
            configToken.isNotBlank() ->
                BridgeTokenResolution(
                    token = configToken,
                    source = BridgeTokenSource.CONFIG_FILE,
                    configPath = configPath,
                    replyImageDir = replyImageDir,
                )

            envToken.isNotBlank() ->
                BridgeTokenResolution(
                    token = envToken,
                    source = BridgeTokenSource.ENV_FALLBACK,
                    configPath = configPath,
                    replyImageDir = replyImageDir,
                )

            else ->
                BridgeTokenResolution(
                    token = "",
                    source = BridgeTokenSource.NONE,
                    configPath = configPath,
                    replyImageDir = replyImageDir,
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

internal fun decodeBridgeToken(rawConfig: String): String =
    decodeBridgeRuntimeConfig(rawConfig)
        ?.bridgeToken
        ?.trim()
        .orEmpty()

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
