package party.qwer.iris

internal data class BridgeRuntimeConfigInputs(
    val envToken: String,
    val configToken: String,
    val configPath: String,
    val replyImageDir: String,
    val textBridgeSendTextEnabled: Boolean,
    val textBridgeSendMarkdownEnabled: Boolean,
)

internal fun resolveBridgeRuntimeConfigInputs(
    env: Map<String, String>,
    fileReader: (String) -> String?,
): BridgeRuntimeConfigInputs {
    val paths = IrisRuntimePathPolicy.resolve(env)
    val explicitConfigPath = env["IRIS_CONFIG_PATH"]?.trim()?.isNotEmpty() == true
    val resolvedConfig =
        readConfigSnapshot(paths.configPath, fileReader)
            ?: if (!explicitConfigPath && paths.configPath != PUBLIC_TMP_CONFIG_PATH) {
                readConfigSnapshot(PUBLIC_TMP_CONFIG_PATH, fileReader)
            } else {
                null
            }
    val configPath = resolvedConfig?.first ?: paths.configPath
    val configSnapshot = resolvedConfig?.second

    return BridgeRuntimeConfigInputs(
        envToken = env["IRIS_BRIDGE_TOKEN"]?.trim().orEmpty(),
        configToken = configSnapshot?.bridgeToken?.trim().orEmpty(),
        configPath = configPath,
        replyImageDir = configSnapshot.replyImageDirOrDefault(paths.replyImageDir),
        textBridgeSendTextEnabled =
            resolveBridgeBooleanFlag(
                env = env,
                envKey = "IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED",
                configValue = configSnapshot?.textBridgeSendTextEnabled,
            ),
        textBridgeSendMarkdownEnabled =
            resolveBridgeBooleanFlag(
                env = env,
                envKey = "IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED",
                configValue = configSnapshot?.textBridgeSendMarkdownEnabled,
            ),
    )
}

private const val PUBLIC_TMP_CONFIG_PATH = "/data/local/tmp/config.json"

private fun readConfigSnapshot(
    configPath: String,
    fileReader: (String) -> String?,
): Pair<String, BridgeRuntimeConfigSnapshot>? =
    runCatching {
        fileReader(configPath)
    }.getOrNull()
        ?.let(::decodeBridgeRuntimeConfig)
        ?.let { configPath to it }

internal fun BridgeRuntimeConfigInputs.toBridgeTokenResolution(): BridgeTokenResolution {
    val token = configToken.takeIf { it.isNotBlank() } ?: envToken
    return BridgeTokenResolution(
        token = token,
        source = tokenSource(token),
        configPath = configPath,
        replyImageDir = replyImageDir,
        textBridgeSendTextEnabled = textBridgeSendTextEnabled,
        textBridgeSendMarkdownEnabled = textBridgeSendMarkdownEnabled,
    )
}

private fun BridgeRuntimeConfigInputs.tokenSource(token: String): BridgeTokenSource =
    when {
        configToken.isNotBlank() -> BridgeTokenSource.CONFIG_FILE
        token.isNotBlank() -> BridgeTokenSource.ENV_FALLBACK
        else -> BridgeTokenSource.NONE
    }

private fun BridgeRuntimeConfigSnapshot?.replyImageDirOrDefault(default: String): String =
    this
        ?.replyImageDir
        ?.trim()
        .orEmpty()
        .ifBlank { default }
