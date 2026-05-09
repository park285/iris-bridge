package party.qwer.iris

data class IrisRuntimePaths(
    val dataDir: String,
    val configPath: String,
    val logDir: String,
    val replyImageDir: String,
    val imageBridgeMuxSocketName: String,
)

object IrisRuntimePathPolicy {
    private const val DEFAULT_DATA_DIR = "/data/iris"
    private const val DEFAULT_CONFIG_FILENAME = "config.json"
    private const val DEFAULT_LOG_DIR = "logs"
    private const val DEFAULT_REPLY_IMAGE_DIR = "/data/iris-tmp/reply-images"
    const val DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME = "iris-image-bridge-mux"

    fun resolve(env: Map<String, String> = System.getenv()): IrisRuntimePaths {
        val dataDir = env.nonBlank("IRIS_DATA_DIR") ?: DEFAULT_DATA_DIR
        val configPath = env.nonBlank("IRIS_CONFIG_PATH") ?: "$dataDir/$DEFAULT_CONFIG_FILENAME"
        val logDir = env.nonBlank("IRIS_LOG_DIR") ?: "$dataDir/$DEFAULT_LOG_DIR"
        val replyImageDir = env.nonBlank("IRIS_REPLY_IMAGE_DIR") ?: DEFAULT_REPLY_IMAGE_DIR
        val imageBridgeMuxSocketName = env.nonBlank("IRIS_IMAGE_BRIDGE_MUX_SOCKET_NAME") ?: DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME
        return IrisRuntimePaths(
            dataDir = dataDir,
            configPath = configPath,
            logDir = logDir,
            replyImageDir = replyImageDir,
            imageBridgeMuxSocketName = imageBridgeMuxSocketName,
        )
    }
}

private fun Map<String, String>.nonBlank(key: String): String? = this[key]?.trim()?.takeIf { it.isNotEmpty() }
