@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.BridgeHandshakeTestFixtures
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BridgeCoreRuntimeTest {
    @Test
    fun `ABI version includes current bridge core JNI surface`() {
        assertEquals(30, BridgeCore.EXPECTED_ABI_VERSION)
    }

    @Test
    fun `protocol contract dispatch returns Rust contract JSON`() {
        val contractJson = assertNotNull(BridgeCore.protocolContractJson())
        val contract = JSONObject(contractJson)

        assertEquals(ImageBridgeProtocol.PROTOCOL_VERSION, contract.getInt("protocolVersion"))
        assertEquals(ImageBridgeProtocol.MAX_FRAME_SIZE, contract.getInt("maxFrameSize"))
        assertEquals(ImageBridgeProtocol.ACTION_SEND_TEXT, contract.getJSONArray("actions").getJSONObject(1).getString("wireName"))
    }

    @Test
    fun `loadOrNull returns runtime whose abi matches and round-trips a context handle`() {
        val runtime = BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true")

        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        try {
            assertEquals(BridgeCore.EXPECTED_ABI_VERSION, BridgeCoreJniContext.nativeAbiVersion())
            assertTrue(runtime.requireHandshake, "production+true must require handshake")
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `close is idempotent and survives repeated calls`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )

        runtime.close()
        runtime.close()
        runtime.close()
    }

    @Test
    fun `development mode without explicit flag does not require handshake`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        try {
            assertFalse(runtime.requireHandshake)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `envelope parsing distinguishes ok and error envelopes`() {
        val ok = BridgeCoreEnvelope.parse("""{"ok":true,"frameJson":"abc"}""")
        assertTrue(ok.isOk)
        assertNull(ok.errorCode)
        assertEquals("abc", ok.string("frameJson"))

        val error = BridgeCoreEnvelope.parse("""{"ok":false,"errorCode":"UNAUTHORIZED","error":"unauthorized bridge token"}""")
        assertFalse(error.isOk)
        assertEquals("UNAUTHORIZED", error.errorCode)
        assertEquals("unauthorized bridge token", error.errorMessage)
    }

    @Test
    fun `dedupe envelope exposes three admission states`() {
        val fresh = BridgeCoreEnvelope.parse("""{"ok":true,"state":"fresh"}""").dedupeState()
        assertEquals(DedupeState.Fresh, fresh)

        val inFlight = BridgeCoreEnvelope.parse("""{"ok":true,"state":"inFlight"}""").dedupeState()
        assertEquals(DedupeState.InFlight, inFlight)

        val cached = BridgeCoreEnvelope.parse("""{"ok":true,"state":"cached","responseJson":"{\"status\":\"sent\"}"}""").dedupeState()
        assertTrue(cached is DedupeState.Cached)
        assertEquals("""{"status":"sent"}""", cached.responseJson)
    }

    @Test
    fun `validateRequestToken dispatch accepts a matching token and rejects a mismatch`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true"),
            )
        try {
            val ok = runtime.validateRequestToken("""{"protocolVersion":1,"token":"bridge-token"}""")
            assertTrue(ok.isOk, "matching token must validate: ${ok.errorMessage}")

            val bad = runtime.validateRequestToken("""{"protocolVersion":1,"token":"wrong"}""")
            assertFalse(bad.isOk)
            assertEquals("unauthorized bridge token", bad.errorMessage)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `dispatch after close fails closed without touching the freed handle`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true"),
            )
        runtime.close()

        val token = runtime.validateRequestToken("""{"protocolVersion":1,"token":"bridge-token"}""")
        assertFalse(token.isOk, "token validation must fail closed after close")
        assertEquals("BRIDGE_CORE_CLOSED", token.errorCode)

        val hello = runtime.handshakeOnHello("""{"type":"hello","protocolVersion":1,"clientNonce":"aa","socketName":"@iris-image-bridge-mux","timestampMs":1}""", 1L, "@iris-image-bridge-mux")
        assertFalse(hello.isOk)
        assertEquals("BRIDGE_CORE_CLOSED", hello.errorCode)

        val proof = runtime.handshakeOnClientProof("""{"type":"client_proof","protocolVersion":1,"proof":"ff"}""")
        assertFalse(proof.isOk)
        assertEquals("BRIDGE_CORE_CLOSED", proof.errorCode)

        val leases = runtime.verifyLeases(1L, "req-1", "[]", "[]", 1L)
        assertFalse(leases.isOk)
        assertEquals("BRIDGE_CORE_CLOSED", leases.errorCode)

        val admit = runtime.dedupeAdmit("send_text:req-1", 1L)
        assertFalse(admit.isOk)
        assertEquals("BRIDGE_CORE_CLOSED", admit.errorCode)
        assertNull(admit.dedupeState(), "closed admit must not report a dedupe state")

        val text =
            runtime.validateTextRequest(
                roomId = 1L,
                message = "hello",
                markdown = false,
                attachmentJson = null,
                mentionsJson = null,
            )
        assertFalse(text.isOk)
        assertEquals("BRIDGE_CORE_CLOSED", text.errorCode)

        val paths =
            runtime.validateImagePaths(
                imagePaths = listOf("/data/iris-tmp/reply-images/req-1/image-0.png"),
                maxPathCount = 8,
                maxPathLength = 4096,
            )
        assertFalse(paths.isOk)
        assertEquals("BRIDGE_CORE_CLOSED", paths.errorCode)

        runtime.dedupeComplete("send_text:req-1", """{"status":"sent"}""", 1L)
    }

    @Test
    fun `dedupe dispatch reports fresh then inFlight and caches a completed response`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        try {
            val key = "send_text:dedupe-dispatch-1"
            val first = runtime.dedupeAdmit(key, 1L)
            assertTrue(first.isOk)
            assertEquals(DedupeState.Fresh, first.dedupeState())

            val second = runtime.dedupeAdmit(key, 2L)
            assertEquals(DedupeState.InFlight, second.dedupeState())

            runtime.dedupeComplete(key, """{"status":"sent"}""", 3L)

            val cached = runtime.dedupeAdmit(key, 4L)
            val state = cached.dedupeState()
            assertTrue(state is DedupeState.Cached)
            assertEquals("""{"status":"sent"}""", state.responseJson)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `request admission dispatch rejects missing request id for side effect actions`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        try {
            val missing = runtime.validateRequestAdmission("send_text", null)
            assertFalse(missing.isOk)
            assertEquals("MISSING_REQUEST_ID", missing.errorCode)
            assertEquals("requestId missing", missing.errorMessage)

            val present = runtime.validateRequestAdmission("send_text", "req-1")
            assertTrue(present.isOk, "nonblank requestId must pass: ${present.errorMessage}")

            val health = runtime.validateRequestAdmission("health", null)
            assertTrue(health.isOk, "health must not require requestId: ${health.errorMessage}")
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `request dedupe key dispatch preserves side effect key policy`() {
        assertEquals("send_text:req-1", BridgeCore.requestDedupeKey("send_text", "req-1"))
        assertEquals("send_text: req-1 ", BridgeCore.requestDedupeKey("send_text", " req-1 "))
        assertNull(BridgeCore.requestDedupeKey("send_text", "  "))
        assertNull(BridgeCore.requestDedupeKey("send_text", null))
        assertNull(BridgeCore.requestDedupeKey("health", "req-1"))
    }

    @Test
    fun `text request validation dispatch normalizes attachment and rejects invalid combinations`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        try {
            val valid =
                runtime.validateTextRequest(
                    roomId = 123L,
                    message = "hello",
                    markdown = false,
                    attachmentJson = """  {"P":{"TP":"List"}}  """,
                    mentionsJson = null,
                )
            assertTrue(valid.isOk, "valid text request must pass: ${valid.errorMessage}")
            assertEquals("""{"P":{"TP":"List"}}""", valid.string("attachmentJson"))

            val invalid =
                runtime.validateTextRequest(
                    roomId = 123L,
                    message = "hello",
                    markdown = true,
                    attachmentJson = """{"P":{"TP":"List"}}""",
                    mentionsJson = null,
                )
            assertFalse(invalid.isOk)
            assertEquals("BAD_REQUEST", invalid.errorCode)
            assertEquals("attachmentJson is only supported for send_text", invalid.errorMessage)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `image path validation dispatch rejects static path policy violations`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        try {
            val valid =
                runtime.validateImagePaths(
                    imagePaths = listOf("/data/iris-tmp/reply-images/req-1/image-0.png"),
                    maxPathCount = 8,
                    maxPathLength = 4096,
                )
            assertTrue(valid.isOk, "valid static path policy must pass: ${valid.errorMessage}")

            val blank =
                runtime.validateImagePaths(
                    imagePaths = listOf("   "),
                    maxPathCount = 8,
                    maxPathLength = 4096,
                )
            assertFalse(blank.isOk)
            assertEquals("PATH_VALIDATION_FAILED", blank.errorCode)
            assertEquals("blank image path", blank.errorMessage)

            val nullByte =
                runtime.validateImagePaths(
                    imagePaths = listOf("/data/iris-tmp/reply-images/req-1/image-\u0000.png"),
                    maxPathCount = 8,
                    maxPathLength = 4096,
                )
            assertFalse(nullByte.isOk)
            assertEquals("PATH_VALIDATION_FAILED", nullByte.errorCode)
            assertEquals("image path contains null byte", nullByte.errorMessage)

            val emojiPath = "😀".repeat(2049)
            val tooLong =
                runtime.validateImagePaths(
                    imagePaths = listOf(emojiPath),
                    maxPathCount = 8,
                    maxPathLength = 4096,
                )
            assertFalse(tooLong.isOk)
            assertEquals("PATH_VALIDATION_FAILED", tooLong.errorCode)
            assertEquals("image path is too long: 4098", tooLong.errorMessage)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `error classification dispatch returns bridge protocol error codes`() {
        assertEquals(
            ImageBridgeProtocol.ERROR_PATH_VALIDATION,
            BridgeCore.classifyErrorCode("image path validation timed out", isIllegalArgument = true),
        )
        assertEquals(
            ImageBridgeProtocol.ERROR_TIMEOUT,
            BridgeCore.classifyErrorCode("CHATROOM OPEN DISPATCH TIMED OUT", isIllegalArgument = false),
        )
        assertEquals(
            ImageBridgeProtocol.ERROR_BAD_REQUEST,
            BridgeCore.classifyErrorCode("bad request from caller", isIllegalArgument = true),
        )
        assertEquals(
            ImageBridgeProtocol.ERROR_SEND_FAILED,
            BridgeCore.classifyErrorCode("send failed", isIllegalArgument = false),
        )
    }

    @Test
    fun `bridge flag truthiness dispatch matches server rollout parsing`() {
        for (raw in listOf("true", "TRUE", " True ", "1", "on", "yes")) {
            assertTrue(BridgeCore.isTruthyFlag(raw), "$raw must be truthy")
        }
        for (raw in listOf("", "false", "0", "off", "no", "enabled", "yes please")) {
            assertFalse(BridgeCore.isTruthyFlag(raw), "$raw must be false")
        }
    }

    @Test
    fun `security mode normalization dispatch returns canonical core raw values`() {
        assertEquals("production", BridgeCore.normalizeSecurityMode(null))
        assertEquals("production", BridgeCore.normalizeSecurityMode("unknown"))
        assertEquals("development", BridgeCore.normalizeSecurityMode(" Development "))
        assertEquals("development", BridgeCore.normalizeSecurityMode("dev"))
    }

    @Test
    fun `allowed peer uid dispatch returns core security mode defaults and configured values`() {
        assertEquals(listOf(0), BridgeCore.allowedPeerUids("production", null).toList())
        assertEquals(listOf(0, 2000), BridgeCore.allowedPeerUids("development", null).toList())
        assertEquals(
            listOf(0, 2000, 3000),
            BridgeCore.allowedPeerUids("dev", "2000, 3000,invalid, 0").toList(),
        )
    }

    @Test
    fun `server restart delay fails closed to max delay when native policy is unavailable`() {
        assertEquals(
            30_000L,
            BridgeCore.serverRestartDelayMs(
                failureCount = 2,
                restartDelayPolicy = { null },
            ),
        )
    }

    @Test
    fun `image path root containment dispatch preserves separator boundary`() {
        val roots = listOf("/data/iris-tmp/reply-images")

        assertTrue(
            BridgeCore.imagePathUnderAllowedRoot(
                "/data/iris-tmp/reply-images/req-1/image-0.png",
                roots,
            ),
        )
        assertFalse(
            BridgeCore.imagePathUnderAllowedRoot(
                "/data/iris-tmp/reply-images-sibling/req-1/image-0.png",
                roots,
            ),
        )
        assertFalse(
            BridgeCore.imagePathUnderAllowedRoot(
                "/data/iris-tmp/reply-images/req-1/image-0.png",
                listOf(""),
            ),
        )
    }

    @Test
    fun `image path materialization dispatch returns canonical snapshot and revalidates changes`() {
        val root = Files.createTempDirectory("iris-core-path-root").toFile()
        val image = Files.createTempFile(root.toPath(), "image", ".png").toFile().apply { writeText("x") }
        val roots = listOf(root.canonicalPath)

        val snapshot = assertNotNull(BridgeCore.materializeImagePath(image.absolutePath, roots))
        assertEquals(image.canonicalPath, snapshot.canonicalPath)
        assertEquals(1L, snapshot.sizeBytes)
        assertTrue(snapshot.lastModifiedEpochMs > 0)

        Thread.sleep(10)
        image.writeText("changed")

        val error =
            assertFailsWith<IllegalArgumentException> {
                BridgeCore.revalidateImagePathSnapshot(
                    snapshot.canonicalPath,
                    roots,
                    snapshot.sizeBytes,
                    snapshot.lastModifiedEpochMs,
                )
            }
        assertEquals("image file changed before send: ${snapshot.canonicalPath}", error.message)

        root.deleteRecursively()
    }

    @Test
    fun `image lease rejection kind dispatch preserves bridge exception policy`() {
        assertTrue(BridgeCore.imageLeaseRejectionIsStateError("image lease required"))
        assertTrue(BridgeCore.imageLeaseRejectionIsStateError("image lease verification failed: EXPIRED"))
        assertFalse(
            BridgeCore.imageLeaseRejectionIsStateError(
                "image lease last modified mismatch: /tmp/a expected=1 actual=2",
            ),
        )
        assertFalse(BridgeCore.imageLeaseRejectionIsStateError("image file not found: /tmp/a"))
    }

    @Test
    fun `image lease rejection kind fails closed when native policy is unavailable`() {
        assertFalse(
            BridgeCore.imageLeaseRejectionIsStateError(
                "image lease verification failed: EXPIRED",
                rejectionKindPolicy = { null },
            ),
        )
    }

    @Test
    fun `image lease facts dispatch returns facts json for canonical paths`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        val root = Files.createTempDirectory("iris-core-lease-facts").toFile()
        val image = Files.createTempFile(root.toPath(), "image", ".png").toFile().apply { writeText("abc") }
        try {
            val envelope = runtime.imageLeaseFactsJson(listOf(image.canonicalPath))
            assertTrue(envelope.isOk, "lease facts must be generated: ${envelope.errorMessage}")
            val facts = JSONArray(assertNotNull(envelope.string("factsJson")))
            val fact = facts.getJSONObject(0)

            assertEquals(image.canonicalPath, fact.getString("canonical_path"))
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", fact.getString("sha256_hex"))
            assertEquals(3L, fact.getLong("byte_length"))
            assertTrue(fact.getLong("last_modified_epoch_ms") > 0L)

            val missing = File(root, "missing.png").path
            val missingEnvelope = runtime.imageLeaseFactsJson(listOf(missing))
            assertFalse(missingEnvelope.isOk)
            assertEquals("PATH_VALIDATION_FAILED", missingEnvelope.errorCode)
            assertEquals("image file not found: $missing", missingEnvelope.errorMessage)
        } finally {
            root.deleteRecursively()
            runtime.close()
        }
    }

    @Test
    fun `send block reason dispatch preserves discovery hook policy`() {
        val hookNames =
            arrayOf(
                "ChatMediaSender#sendMultiple",
                "ChatMediaSender#threadedEntry",
                "ChatMediaSender#threadedInject",
            )
        val hookInstalled = booleanArrayOf(true, true, false)

        assertEquals(
            "bridge discovery hooks not installed",
            BridgeCore.sendBlockReason(false, emptyArray(), booleanArrayOf(), 1, null, null),
        )
        assertNull(BridgeCore.sendBlockReason(true, hookNames, hookInstalled, 1, null, null))
        assertEquals(
            "bridge discovery hook not ready: ChatMediaSender#threadedInject",
            BridgeCore.sendBlockReason(true, hookNames, hookInstalled, 1, 55L, 2),
        )
    }

    @Test
    fun `KakaoLink leverage encryption type dispatch preserves metadata policy`() {
        assertEquals(31, BridgeCore.kakaoLinkLeverageEncryptionType("""{"enc":31}"""))
        assertEquals(42, BridgeCore.kakaoLinkLeverageEncryptionType("""{ "enc" : 42 }"""))
        assertEquals(31, BridgeCore.kakaoLinkLeverageEncryptionType("""{"enc":"42"}"""))
        assertEquals(31, BridgeCore.kakaoLinkLeverageEncryptionType("""{"enc":-1}"""))
        assertEquals(31, BridgeCore.kakaoLinkLeverageEncryptionType("not-json"))
    }

    @Test
    fun `current bridge capabilities dispatch preserves rollout and readiness policy`() {
        val envelope =
            assertNotNull(
                BridgeCore.currentBridgeCapabilities(
                    registryAvailable = true,
                    registryError = null,
                    specReady = true,
                    textSupported = true,
                    textReady = true,
                    textReason = null,
                    sendTextEnabled = false,
                    sendMarkdownEnabled = true,
                ),
            )

        assertTrue(envelope.isOk, "capabilities must be generated: ${envelope.errorMessage}")
        assertTrue(assertNotNull(envelope.bool("inspectChatRoomReady")))
        assertFalse(assertNotNull(envelope.bool("sendTextReady")))
        assertEquals("text bridge send_text disabled", envelope.string("sendTextReason"))
        assertTrue(assertNotNull(envelope.bool("sendMarkdownReady")))
        assertEquals(null, envelope.string("sendMarkdownReason"))
    }

    @Test
    fun `handshake hello then client proof round-trips through the native library`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true"),
            )
        try {
            val helloFrame =
                """{"type":"hello","protocolVersion":1,"clientNonce":"client-nonce","socketName":"@iris-image-bridge-mux","timestampMs":1}"""
            val helloEnvelope =
                BridgeCoreEnvelope.parse(
                    BridgeCoreJniContext.nativeHandshakeOnHello(
                        runtime.handle,
                        helloFrame,
                        1_000L,
                        "@iris-image-bridge-mux",
                    ),
                )
            assertTrue(helloEnvelope.isOk, "hello must be accepted: ${helloEnvelope.errorMessage}")

            val serverFrameJson = assertNotNull(helloEnvelope.string("frameJson"))
            val serverNonce = assertNotNull(BridgeCoreEnvelope.parse(serverFrameJson).string("serverNonce"))
            val proof =
                BridgeHandshakeTestFixtures.clientProof(
                    bridgeToken = "bridge-token",
                    clientNonce = "client-nonce",
                    serverNonce = serverNonce,
                )
            val proofFrame = """{"type":"client_proof","protocolVersion":1,"proof":"$proof"}"""
            val proofEnvelope =
                BridgeCoreEnvelope.parse(
                    BridgeCoreJniContext.nativeHandshakeOnClientProof(runtime.handle, proofFrame),
                )
            assertTrue(proofEnvelope.isOk, "client proof must authenticate: ${proofEnvelope.errorMessage}")
        } finally {
            runtime.close()
        }
    }
}
