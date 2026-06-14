package party.qwer.iris

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
    ): BridgeTokenResolution =
        resolveBridgeRuntimeConfigInputs(env = env, fileReader = fileReader)
            .toBridgeTokenResolution()
}

fun resolveBridgeToken(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): String = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).token

fun resolveBridgeReplyImageDir(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): String = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).replyImageDir

fun resolveBridgeTextSendTextEnabled(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): Boolean = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).textBridgeSendTextEnabled

fun resolveBridgeTextSendMarkdownEnabled(
    env: Map<String, String> = System.getenv(),
    fileReader: (String) -> String? = ::readBridgeRuntimeConfigFile,
): Boolean = BridgeBootstrapConfigResolver.resolve(env = env, fileReader = fileReader).textBridgeSendMarkdownEnabled
