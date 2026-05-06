package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals

class IrisRuntimePathPolicyTest {
    @Test
    fun `resolve returns default runtime paths`() {
        val paths = IrisRuntimePathPolicy.resolve(emptyMap())

        assertEquals("/data/iris", paths.dataDir)
        assertEquals("/data/iris/config.json", paths.configPath)
        assertEquals("/data/iris/logs", paths.logDir)
        assertEquals("/data/iris/reply-images", paths.replyImageDir)
        assertEquals("iris-image-bridge", paths.imageBridgeSocketName)
        assertEquals("iris-image-bridge-mux", paths.imageBridgeMuxSocketName)
    }

    @Test
    fun `resolve honors IRIS_DATA_DIR base`() {
        val paths = IrisRuntimePathPolicy.resolve(mapOf("IRIS_DATA_DIR" to "/custom/iris"))

        assertEquals("/custom/iris", paths.dataDir)
        assertEquals("/custom/iris/config.json", paths.configPath)
        assertEquals("/custom/iris/logs", paths.logDir)
        assertEquals("/custom/iris/reply-images", paths.replyImageDir)
    }

    @Test
    fun `resolve honors explicit config and log overrides over IRIS_DATA_DIR`() {
        val paths =
            IrisRuntimePathPolicy.resolve(
                mapOf(
                    "IRIS_DATA_DIR" to "/custom/iris",
                    "IRIS_CONFIG_PATH" to "/opt/iris/config.json",
                    "IRIS_LOG_DIR" to "/var/log/iris",
                    "IRIS_REPLY_IMAGE_DIR" to "/var/lib/iris/images",
                    "IRIS_IMAGE_BRIDGE_SOCKET_NAME" to "iris-image-bridge-dev",
                    "IRIS_IMAGE_BRIDGE_MUX_SOCKET_NAME" to "iris-image-bridge-mux-dev",
                ),
            )

        assertEquals("/custom/iris", paths.dataDir)
        assertEquals("/opt/iris/config.json", paths.configPath)
        assertEquals("/var/log/iris", paths.logDir)
        assertEquals("/var/lib/iris/images", paths.replyImageDir)
        assertEquals("iris-image-bridge-dev", paths.imageBridgeSocketName)
        assertEquals("iris-image-bridge-mux-dev", paths.imageBridgeMuxSocketName)
    }
}
