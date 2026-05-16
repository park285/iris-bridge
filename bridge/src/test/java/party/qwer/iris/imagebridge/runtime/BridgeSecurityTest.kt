@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeHandshakeFrame
import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.send.ImageSendRequest
import party.qwer.iris.imagebridge.runtime.send.KakaoImageSender
import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvoker
import party.qwer.iris.imagebridge.runtime.server.BridgeHandshakeValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeImagePathValidator
import party.qwer.iris.imagebridge.runtime.server.BridgePeerIdentityValidator
import party.qwer.iris.imagebridge.runtime.server.BridgeSecurityMode
import party.qwer.iris.imagebridge.runtime.server.BridgeSocketHandshakeAuthenticator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeSecurityTest {
    @Test
    fun `security mode defaults to production unless development is explicitly requested`() {
        assertEquals(BridgeSecurityMode.PRODUCTION, BridgeSecurityMode.fromEnv(null))
        assertEquals(BridgeSecurityMode.PRODUCTION, BridgeSecurityMode.fromEnv("unknown"))
        assertEquals(BridgeSecurityMode.DEVELOPMENT, BridgeSecurityMode.fromEnv("development"))
    }

    @Test
    fun `peer validator rejects unauthorized uid`() {
        val validator = BridgePeerIdentityValidator(setOf(2000))

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(1000)
            }

        assertEquals("unauthorized bridge client uid=1000", error.message)
    }

    @Test
    fun `production mode requires configured token`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "",
                securityMode = BridgeSecurityMode.PRODUCTION,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(healthRequest(token = ""))
            }

        assertEquals("bridge token must be configured in production mode", error.message)
    }

    @Test
    fun `production mode rejects mismatched token`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "secret",
                securityMode = BridgeSecurityMode.PRODUCTION,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(healthRequest(token = "wrong"))
            }

        assertEquals("unauthorized bridge token", error.message)
    }

    @Test
    fun `production mode accepts matching token`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "secret",
                securityMode = BridgeSecurityMode.PRODUCTION,
            )

        validator.validate(healthRequest(token = "secret"))
    }

    @Test
    fun `development mode skips token check when blank`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "",
                securityMode = BridgeSecurityMode.DEVELOPMENT,
            )

        validator.validate(healthRequest(token = ""))
    }

    @Test
    fun `development mode checks token when configured`() {
        val validator =
            BridgeHandshakeValidator(
                expectedToken = "secret",
                securityMode = BridgeSecurityMode.DEVELOPMENT,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(healthRequest(token = "wrong"))
            }

        assertEquals("unauthorized bridge token", error.message)
    }

    @Test
    fun `socket authenticator accepts matching client proof before request handling`() {
        val input = ByteArrayOutputStream()
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeProtocol.buildHello(
                clientNonce = "client-nonce",
                socketName = "iris-image-bridge-mux",
                timestampMs = 1234L,
            ),
        )
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeProtocol.buildClientProof(
                bridgeToken = "bridge-token",
                clientNonce = "client-nonce",
                serverNonce = "server-nonce",
            ),
        )
        val output = ByteArrayOutputStream()
        val authenticator =
            BridgeSocketHandshakeAuthenticator(
                expectedToken = "bridge-token",
                securityMode = BridgeSecurityMode.PRODUCTION,
                nonceFactory = { "server-nonce" },
            )

        authenticator.authenticate(ByteArrayInputStream(input.toByteArray()), output, "iris-image-bridge-mux")

        val serverProof = ImageBridgeHandshakeProtocol.readFrame(ByteArrayInputStream(output.toByteArray()))
        assertEquals(ImageBridgeHandshakeProtocol.TYPE_SERVER_PROOF, serverProof.type)
        assertTrue(
            ImageBridgeHandshakeProtocol.proofMatches(
                serverProof.proof,
                ImageBridgeHandshakeProtocol.serverProof(
                    bridgeToken = "bridge-token",
                    clientNonce = "client-nonce",
                    serverNonce = "server-nonce",
                    socketName = "iris-image-bridge-mux",
                ),
            ),
        )
    }

    @Test
    fun `socket authenticator rejects bad client proof with sanitized error`() {
        val input = ByteArrayOutputStream()
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeProtocol.buildHello(
                clientNonce = "client-nonce",
                socketName = "iris-image-bridge-mux",
                timestampMs = 1234L,
            ),
        )
        ImageBridgeHandshakeProtocol.writeFrame(
            input,
            ImageBridgeHandshakeFrame(
                type = ImageBridgeHandshakeProtocol.TYPE_CLIENT_PROOF,
                protocolVersion = ImageBridgeProtocol.PROTOCOL_VERSION,
                proof = "bad-proof",
            ),
        )
        val authenticator =
            BridgeSocketHandshakeAuthenticator(
                expectedToken = "bridge-token",
                securityMode = BridgeSecurityMode.PRODUCTION,
                nonceFactory = { "server-nonce" },
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                authenticator.authenticate(ByteArrayInputStream(input.toByteArray()), ByteArrayOutputStream(), "iris-image-bridge-mux")
            }

        assertEquals(ImageBridgeHandshakeProtocol.AUTHENTICATION_FAILED, error.message)
    }

    @Test
    fun `default allowed uids include configured values in addition to development defaults`() {
        val allowed = BridgePeerIdentityValidator.defaultAllowedUids("2000, 3000")

        assertTrue(allowed.contains(0))
        assertTrue(allowed.contains(2000))
        assertTrue(allowed.contains(3000))
    }

    @Test
    fun `production mode allows root uid by default`() {
        val validator =
            BridgePeerIdentityValidator(
                securityMode = BridgeSecurityMode.PRODUCTION,
                extraUidsRaw = null,
            )

        validator.validate(0)
    }

    @Test
    fun `production mode accepts explicitly allowed uid`() {
        val validator =
            BridgePeerIdentityValidator(
                securityMode = BridgeSecurityMode.PRODUCTION,
                extraUidsRaw = "0,2000",
            )

        validator.validate(0)
    }

    @Test
    fun `development mode allows root and shell by default`() {
        val validator =
            BridgePeerIdentityValidator(
                securityMode = BridgeSecurityMode.DEVELOPMENT,
                extraUidsRaw = null,
            )

        validator.validate(0)
        validator.validate(2000)
    }

    @Test
    fun `path validator rejects files outside allowed root`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val outsideFile = Files.createTempFile("iris-outside", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(listOf(outsideFile.absolutePath))
            }

        assertTrue(error.message?.contains("outside allowed root") == true)
        outsideFile.delete()
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator accepts files inside allowed root`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val insideFile = Files.createTempFile(allowedDir.toPath(), "iris-inside", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val validated = validator.validate(listOf(insideFile.absolutePath))

        assertEquals(listOf(insideFile.canonicalPath), validated.map { it.canonicalPath })
        insideFile.delete()
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator accepts files inside any allowed root`() {
        val legacyDir = Files.createTempDirectory("iris-legacy-allowed").toFile()
        val runtimeDir = Files.createTempDirectory("iris-runtime-allowed").toFile()
        val runtimeFile = Files.createTempFile(runtimeDir.toPath(), "iris-runtime", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(listOf(legacyDir.absolutePath, runtimeDir.absolutePath))

        val validated = validator.validate(listOf(runtimeFile.absolutePath))

        assertEquals(listOf(runtimeFile.canonicalPath), validated.map { it.canonicalPath })
        runtimeFile.delete()
        legacyDir.deleteRecursively()
        runtimeDir.deleteRecursively()
    }

    @Test
    fun `default path validator allows native runtime reply image root`() {
        assertTrue(BridgeImagePathValidator.DEFAULT_ALLOWED_IMAGE_ROOTS.contains("/data/iris-tmp/reply-images"))
        assertFalse(BridgeImagePathValidator.DEFAULT_ALLOWED_IMAGE_ROOTS.contains(BridgeImagePathValidator.LEGACY_OUTBOX_IMAGE_ROOT))
    }

    @Test
    fun `default path roots honor runtime data dir policy`() {
        assertEquals(
            listOf("/data/iris-tmp/reply-images"),
            BridgeImagePathValidator.defaultAllowedImageRoots(mapOf("IRIS_DATA_DIR" to "/custom/iris")),
        )
    }

    @Test
    fun `default path roots prefer configured reply image directory`() {
        assertEquals(
            listOf("/config/iris/images"),
            BridgeImagePathValidator.defaultAllowedImageRoots(
                env = mapOf("IRIS_CONFIG_PATH" to "/tmp/config.json"),
                fileReader = { """{"replyImageDir":"/config/iris/images"}""" },
            ),
        )
    }

    @Test
    fun `path validator rejects too many paths`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val files =
            (0..BridgeImagePathValidator.MAX_IMAGE_PATH_COUNT).map { index ->
                Files.createTempFile(allowedDir.toPath(), "iris-$index", ".png").toFile().apply { writeText("x") }
            }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(files.map { it.absolutePath })
            }

        assertTrue(error.message?.contains("too many image paths") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator rejects too long path`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(listOf("/data/iris-tmp/reply-images/" + "a".repeat(BridgeImagePathValidator.MAX_IMAGE_PATH_LENGTH)))
            }

        assertTrue(error.message?.contains("too long") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `path validator rejects symlink paths`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val target = Files.createTempFile(allowedDir.toPath(), "iris-target", ".png")
        Files.write(target, byteArrayOf(1))
        val link = allowedDir.toPath().resolve("iris-link.png")
        Files.createSymbolicLink(link, target.fileName)
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                validator.validate(listOf(link.toString()))
            }

        assertTrue(error.message?.contains("symbolic link") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `validated path rejects changed file before send`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val image = Files.createTempFile(allowedDir.toPath(), "iris-image", ".png").toFile().apply { writeText("x") }
        val validator = BridgeImagePathValidator(allowedDir.absolutePath)
        val validated = validator.validate(listOf(image.absolutePath)).single()

        Thread.sleep(2)
        image.writeText("changed")

        val error =
            assertFailsWith<IllegalArgumentException> {
                validated.revalidate()
            }

        assertTrue(error.message?.contains("changed before send") == true)
        allowedDir.deleteRecursively()
    }

    @Test
    fun `kakao image sender receives canonical path only`() {
        val allowedDir = Files.createTempDirectory("iris-allowed").toFile()
        val image = Files.createTempFile(allowedDir.toPath(), "iris-image", ".png").toFile().apply { writeText("x") }
        val validated = BridgeImagePathValidator(allowedDir.absolutePath).validate(listOf(image.absolutePath))
        val sent = mutableListOf<String>()
        val sender =
            KakaoImageSender(
                chatRoomResolver = { Any() },
                sendInvocationFactory =
                    object : KakaoSendInvoker {
                        override fun sendSingle(
                            chatRoom: Any,
                            imagePath: String,
                            threadId: Long?,
                            threadScope: Int?,
                        ) {
                            sent += imagePath
                        }

                        override fun sendMultiple(
                            chatRoom: Any,
                            imagePaths: List<String>,
                            threadId: Long?,
                            threadScope: Int?,
                        ) {
                            sent += imagePaths
                        }

                        override fun sendThreaded(
                            roomId: Long,
                            chatRoom: Any,
                            imagePaths: List<String>,
                            threadId: Long,
                            threadScope: Int,
                        ) {
                            sent += imagePaths
                        }
                    },
                logInfo = { _, _ -> },
            )

        sender.send(
            ImageSendRequest(
                roomId = 1L,
                imagePaths = validated,
                threadId = null,
                threadScope = null,
                requestId = "req-canonical",
            ),
        )

        assertEquals(listOf(image.canonicalPath), sent)
        allowedDir.deleteRecursively()
    }
}
