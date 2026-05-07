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
    val configSnapshot =
        runCatching {
            fileReader(paths.configPath)
        }.getOrNull()
            ?.let(::decodeBridgeRuntimeConfig)

    return BridgeRuntimeConfigInputs(
        envToken = env["IRIS_BRIDGE_TOKEN"]?.trim().orEmpty(),
        configToken = configSnapshot?.bridgeToken?.trim().orEmpty(),
        configPath = paths.configPath,
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
