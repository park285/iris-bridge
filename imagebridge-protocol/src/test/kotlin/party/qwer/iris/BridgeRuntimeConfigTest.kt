package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals

class BridgeRuntimeConfigTest {
    @Test
    fun `bridge resolution falls back to env token when config is unreadable`() {
        val resolution =
            BridgeBootstrapConfigResolver.resolve(
                env =
                    mapOf(
                        "IRIS_BRIDGE_TOKEN" to "bridge-secret",
                        "IRIS_CONFIG_PATH" to "/tmp/missing.json",
                    ),
                fileReader = { null },
            )

        assertEquals("bridge-secret", resolution.token)
        assertEquals(BridgeTokenSource.ENV_FALLBACK, resolution.source)
        assertEquals("/tmp/missing.json", resolution.configPath)
        assertEquals("/data/iris/reply-images", resolution.replyImageDir)
    }

    @Test
    fun `prefers config bridge token over env fallback`() {
        val resolution =
            BridgeBootstrapConfigResolver.resolve(
                env =
                    mapOf(
                        "IRIS_CONFIG_PATH" to "/tmp/config.json",
                        "IRIS_BRIDGE_TOKEN" to "env-bridge-token",
                    ),
                fileReader = {
                    """
                    {
                      "bridgeToken": "config-bridge-token"
                    }
                    """.trimIndent()
                },
            )

        assertEquals("config-bridge-token", resolution.token)
        assertEquals(BridgeTokenSource.CONFIG_FILE, resolution.source)
        assertEquals("/tmp/config.json", resolution.configPath)
    }

    @Test
    fun `falls back to env bridge token when config field is absent`() {
        val resolution =
            BridgeBootstrapConfigResolver.resolve(
                env =
                    mapOf(
                        "IRIS_CONFIG_PATH" to "/tmp/config.json",
                        "IRIS_BRIDGE_TOKEN" to "env-bridge-token",
                    ),
                fileReader = {
                    """
                    {
                      "botControlToken": "legacy-control-token"
                    }
                    """.trimIndent()
                },
            )

        assertEquals("env-bridge-token", resolution.token)
        assertEquals(BridgeTokenSource.ENV_FALLBACK, resolution.source)
        assertEquals("/tmp/config.json", resolution.configPath)
    }

    @Test
    fun `returns blank token when config is unreadable and env is absent`() {
        val resolution =
            BridgeBootstrapConfigResolver.resolve(
                env = emptyMap(),
                fileReader = { null },
            )

        assertEquals("", resolution.token)
        assertEquals(BridgeTokenSource.NONE, resolution.source)
        assertEquals("/data/iris/config.json", resolution.configPath)
        assertEquals(true, resolution.textBridgeSendTextEnabled)
        assertEquals(true, resolution.textBridgeSendMarkdownEnabled)
    }

    @Test
    fun `shared runtime path policy drives bridge config default path`() {
        assertEquals(
            IrisRuntimePathPolicy.resolve(emptyMap()).configPath,
            BridgeBootstrapConfigResolver.resolve(env = emptyMap(), fileReader = { null }).configPath,
        )
    }

    @Test
    fun `prefers config reply image directory over env path policy`() {
        val resolution =
            BridgeBootstrapConfigResolver.resolve(
                env =
                    mapOf(
                        "IRIS_DATA_DIR" to "/env/iris",
                        "IRIS_CONFIG_PATH" to "/tmp/config.json",
                    ),
                fileReader = {
                    """
                    {
                      "replyImageDir": "/config/iris/images"
                    }
                    """.trimIndent()
                },
            )

        assertEquals("/config/iris/images", resolution.replyImageDir)
    }

    @Test
    fun `falls back to runtime path policy when config reply image directory is blank`() {
        val resolution =
            BridgeBootstrapConfigResolver.resolve(
                env = mapOf("IRIS_DATA_DIR" to "/env/iris"),
                fileReader = { """{"replyImageDir":"   "}""" },
            )

        assertEquals("/env/iris/reply-images", resolution.replyImageDir)
    }

    @Test
    fun `resolves bridge text capability flags from config and lets env override`() {
        val configResolution =
            BridgeBootstrapConfigResolver.resolve(
                env = mapOf("IRIS_CONFIG_PATH" to "/tmp/config.json"),
                fileReader = {
                    """
                    {
                      "textBridgeSendTextEnabled": true,
                      "textBridgeSendMarkdownEnabled": false
                    }
                    """.trimIndent()
                },
            )

        assertEquals(true, configResolution.textBridgeSendTextEnabled)
        assertEquals(false, configResolution.textBridgeSendMarkdownEnabled)

        val envResolution =
            BridgeBootstrapConfigResolver.resolve(
                env =
                    mapOf(
                        "IRIS_CONFIG_PATH" to "/tmp/config.json",
                        "IRIS_TEXT_BRIDGE_SEND_TEXT_ENABLED" to "0",
                        "IRIS_TEXT_BRIDGE_SEND_MARKDOWN_ENABLED" to "yes",
                    ),
                fileReader = {
                    """
                    {
                      "textBridgeSendTextEnabled": true,
                      "textBridgeSendMarkdownEnabled": false
                    }
                    """.trimIndent()
                },
            )

        assertEquals(false, envResolution.textBridgeSendTextEnabled)
        assertEquals(true, envResolution.textBridgeSendMarkdownEnabled)
    }
}
