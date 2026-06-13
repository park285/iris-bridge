@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.ImageLeasePayload
import party.qwer.iris.SignedImageLease
import party.qwer.iris.imagebridge.runtime.BridgeHandshakeTestFixtures
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile
import party.qwer.iris.imagebridge.runtime.room.memberextract.ElementView
import party.qwer.iris.imagebridge.runtime.room.memberextract.PrimitiveValue
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
        assertEquals(39, BridgeCore.EXPECTED_ABI_VERSION)
    }

    private fun muxRequestFrame(correlationId: String): String =
        JSONObject()
            .put("type", "request")
            .put("muxVersion", 2)
            .put("correlationId", correlationId)
            .put("request", JSONObject())
            .toString()

    @Test
    fun `Kakao chat log attachment crypto requires compatible bridge core ABI before native dispatch`() {
        var nativeCalled = false

        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.encryptKakaoChatLogAttachment(
                    encType = 31,
                    plaintext = "test",
                    userId = 438_562_408L,
                    loadCompatibleCore = { false },
                    nativeEncrypt = { _, _, _ ->
                        nativeCalled = true
                        """{"ok":true,"attachment":"unused"}"""
                    },
                )
            }

        assertFalse(nativeCalled, "native crypto must not run when ABI compatibility is unavailable")
        assertEquals("bridge core unavailable to encrypt Kakao chat log attachment", error.message)
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
    fun `mux session adapter exposes core commands and duplicate fail closed rule`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        val session = assertNotNull(runtime.createMuxSession(maxInFlight = 1))
        try {
            assertEquals(
                BridgeCoreMuxCommand.Dispatch("same"),
                session.onFrame(muxRequestFrame("same")).muxCommand(),
            )

            assertEquals(
                BridgeCoreMuxCommand.WriteBadRequest(
                    correlationId = "same",
                    message = "duplicate mux correlation id",
                ),
                session.onFrame(muxRequestFrame("same")).muxCommand(),
            )

            assertEquals(
                BridgeCoreMuxCommand.WriteBusy("other"),
                session.onFrame(muxRequestFrame("other")).muxCommand(),
            )

            assertTrue(session.onRequestCompleted("same").isOk)
            assertEquals(
                BridgeCoreMuxCommand.Dispatch("other"),
                session.onFrame(muxRequestFrame("other")).muxCommand(),
            )
        } finally {
            session.close()
            runtime.close()
        }
    }

    @Test
    fun `mux session adapter tracks cancel rejection and closed state`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "development", bridgeToken = "bridge-token", requireHandshakeRaw = null),
            )
        val session = assertNotNull(runtime.createMuxSession(maxInFlight = 2))
        try {
            assertEquals(BridgeCoreMuxCommand.Dispatch("c1"), session.onFrame(muxRequestFrame("c1")).muxCommand())
            assertEquals(
                BridgeCoreMuxCommand.MarkCancelled("c1"),
                session.onFrame("""{"type":"cancel","muxVersion":2,"correlationId":"c1"}""").muxCommand(),
            )
            assertEquals(true, session.isCancelled("c1").strictBool("cancelled"))

            assertEquals(
                BridgeCoreMuxCommand.WriteBusy("c1"),
                session.onExecutorRejected("c1").muxCommand(),
            )
            assertEquals(false, session.isCancelled("c1").strictBool("cancelled"))

            session.close()
            val afterClose = session.onFrame(muxRequestFrame("after-close"))
            assertFalse(afterClose.isOk)
            assertEquals(BRIDGE_CORE_CLOSED_CODE, afterClose.errorCode)
            assertNull(afterClose.muxCommand())
        } finally {
            session.close()
            runtime.close()
        }
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
    fun `request id requirement fails closed when native policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.requestRequiresRequestId("send_text") { null }
            }

        assertEquals("bridge core unavailable to resolve request id requirement", error.message)
    }

    @Test
    fun `request id requirement injection preserves native policy value`() {
        assertFalse(BridgeCore.requestRequiresRequestId("health") { false })
        assertTrue(BridgeCore.requestRequiresRequestId("send_text") { true })
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
    fun `media content policy dispatch normalizes kinds and share manager rejection`() {
        val normalized = BridgeCore.normalizeMediaContentTypes(1, listOf(" Video/MP4 ; charset=utf-8 "))
        assertEquals(listOf("video/mp4"), normalized)
        assertEquals(BridgeCoreMediaMessageKind.Video, BridgeCore.mediaMessageKind(1, normalized))

        val shareManagerError =
            assertFailsWith<IllegalArgumentException> {
                BridgeCore.validateShareManagerImageMedia(normalized)
            }
        assertEquals("video media send is not supported on ShareManager image path", shareManagerError.message)

        val multiVideoError =
            assertFailsWith<IllegalArgumentException> {
                BridgeCore.mediaMessageKind(2, listOf("video/mp4", "image/png"))
            }
        assertEquals("multiple video media send is not supported", multiVideoError.message)
    }

    @Test
    fun `media content policy dispatch normalizes lease content types by image index`() {
        val normalized =
            BridgeCore.mediaContentTypesFromLeases(
                imageCount = 3,
                imageLeases =
                    listOf(
                        signedImageLeaseForMediaPolicy(imageIndex = 2, contentType = " IMAGE/JPEG "),
                        signedImageLeaseForMediaPolicy(imageIndex = 0, contentType = " Video/MP4 ; charset=utf-8 "),
                    ),
            )

        assertEquals(listOf("video/mp4", "", "image/jpeg"), normalized)
    }

    @Test
    fun `reply pending context dispatch returns verified markdown context`() {
        val signature =
            assertNotNull(
                BridgeCore.replyHookSign(
                    bridgeToken = "bridge-token",
                    roomId = 42L,
                    messageText = "markdown body",
                    sessionId = "req-md",
                    createdAtEpochMs = 1_000L,
                    mentionsHash = null,
                ),
            )
        val context =
            assertNotNull(
                BridgeCore.replyMarkdownPendingContextJson(
                    JSONObject()
                        .put("bridgeToken", "bridge-token")
                        .put("nowEpochMs", 1_001L)
                        .put(
                            "snapshot",
                            JSONObject()
                                .put("sessionId", "req-md")
                                .put("roomIdRaw", "42")
                                .put("threadIdRaw", "777")
                                .put("threadScope", 3)
                                .put("createdAtEpochMs", 1_000L)
                                .put("signature", signature)
                                .put("messageText", "markdown body"),
                        ).toString(),
                ),
            )

        assertEquals(42L, context.getLong("roomId"))
        assertEquals("markdown body", context.getString("messageText"))
        assertEquals(777L, context.getLong("threadId"))
        assertEquals(3, context.getInt("threadScope"))
        assertEquals("req-md", context.getString("sessionId"))
    }

    @Test
    fun `reply pending context dispatch returns verified mention context`() {
        val attachment = """{"mentions":[{"userId":7,"nickname":"A"}]}"""
        val mentionsHash = assertNotNull(BridgeCore.mentionsHashFromAttachment(attachment))
        val signature =
            assertNotNull(
                BridgeCore.replyHookSign(
                    bridgeToken = "bridge-token",
                    roomId = 42L,
                    messageText = "hi @A",
                    sessionId = "req-mention",
                    createdAtEpochMs = 2_000L,
                    mentionsHash = mentionsHash,
                ),
            )
        val context =
            assertNotNull(
                BridgeCore.replyMentionPendingContextJson(
                    JSONObject()
                        .put("bridgeToken", "bridge-token")
                        .put("nowEpochMs", 2_001L)
                        .put(
                            "snapshot",
                            JSONObject()
                                .put("sessionId", "req-mention")
                                .put("roomIdRaw", "42")
                                .put("createdAtEpochMs", 2_000L)
                                .put("signature", signature)
                                .put("messageText", "hi @A")
                                .put("attachmentText", attachment),
                        ).toString(),
                ),
            )

        assertEquals(42L, context.getLong("roomId"))
        assertEquals("hi @A", context.getString("messageText"))
        assertEquals("req-mention", context.getString("sessionId"))
        assertEquals(7L, JSONObject(context.getString("attachmentText")).getJSONArray("mentions").getJSONObject(0).getLong("userId"))
    }

    @Test
    fun `reply pending context dispatch returns null when compatible core is unavailable`() {
        var nativeCalled = false

        assertNull(
            BridgeCore.replyMarkdownPendingContextJson(
                requestJson = "{}",
                loadCompatibleCore = { false },
                nativePendingContext = {
                    nativeCalled = true
                    """{"ok":true,"context":{}}"""
                },
            ),
        )
        assertNull(
            BridgeCore.replyMentionPendingContextJson(
                requestJson = "{}",
                loadCompatibleCore = { false },
                nativePendingContext = {
                    nativeCalled = true
                    """{"ok":true,"context":{}}"""
                },
            ),
        )
        assertFalse(nativeCalled)
    }

    @Test
    fun `reply pending context dispatch returns null for native rejection envelopes`() {
        val rejection = """{"ok":false,"errorCode":"BAD_REQUEST","error":"reply pending context snapshot missing"}"""

        assertNull(
            BridgeCore.replyMarkdownPendingContextJson(
                requestJson = "{}",
                loadCompatibleCore = { true },
                nativePendingContext = { rejection },
            ),
        )
        assertNull(
            BridgeCore.replyMentionPendingContextJson(
                requestJson = "{}",
                loadCompatibleCore = { true },
                nativePendingContext = { rejection },
            ),
        )
    }

    @Test
    fun `reply pending context dispatch returns null for malformed native envelopes`() {
        assertNull(
            BridgeCore.replyMarkdownPendingContextJson(
                requestJson = "{}",
                loadCompatibleCore = { true },
                nativePendingContext = { "not-json" },
            ),
        )
        assertNull(
            BridgeCore.replyMentionPendingContextJson(
                requestJson = "{}",
                loadCompatibleCore = { true },
                nativePendingContext = { "not-json" },
            ),
        )
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

    private fun signedImageLeaseForMediaPolicy(
        imageIndex: Int,
        contentType: String,
    ): SignedImageLease =
        SignedImageLease(
            payload =
                ImageLeasePayload(
                    version = 1,
                    requestId = "req-1",
                    roomId = 123L,
                    imageIndex = imageIndex,
                    canonicalPath = "/data/iris-tmp/reply-images/req-1/image-$imageIndex.png",
                    sha256Hex = "00",
                    byteLength = 1L,
                    contentType = contentType,
                    lastModifiedEpochMs = 1L,
                    expiresAtEpochMs = 2L,
                    nonce = "nonce-$imageIndex",
                ),
            signature = "unused",
        )

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
    fun `bridge flag truthiness fails closed when native policy is unavailable`() {
        assertTrue(BridgeCore.isTruthyFlag("true") { true })

        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.isTruthyFlag("true") { null }
            }

        assertEquals("bridge core unavailable to parse bridge flag", error.message)
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
    fun `security mode normalization fails closed when native policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.normalizeSecurityMode(raw = "development") { null }
            }

        assertEquals("bridge core unavailable to normalize security mode", error.message)
    }

    @Test
    fun `allowed peer uid dispatch fails closed when native policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.allowedPeerUids(
                    securityModeRaw = "production",
                    extraUidsRaw = null,
                    peerUidPolicy = { _, _ -> null },
                )
            }

        assertEquals("bridge core unavailable to resolve allowed peer uids", error.message)
    }

    @Test
    fun `server restart delay fails closed when native policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.serverRestartDelayMs(
                    failureCount = 2,
                    restartDelayPolicy = { null },
                )
            }

        assertEquals("bridge core unavailable to resolve restart delay", error.message)
    }

    @Test
    fun `KakaoLink leverage encryption type fails closed when native policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.kakaoLinkLeverageEncryptionType(
                    value = """{"enc":42}""",
                    encryptionTypePolicy = { null },
                )
            }

        assertEquals("bridge core unavailable to resolve KakaoLink leverage encryption type", error.message)
    }

    @Test
    fun `KakaoLink template boolean policy fails closed when native policy is unavailable`() {
        assertTrue(BridgeCore.hasKakaoLinkExplicitTemplateArgs("""{"template_args":{}}""") { true })

        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.hasKakaoLinkExplicitTemplateArgs("""{"template_args":{}}""") { null }
            }

        assertEquals("bridge core unavailable to evaluate KakaoLink explicit template args", error.message)
    }

    @Test
    fun `KakaoLink attachment match policy fails closed when native policy is unavailable`() {
        assertFalse(BridgeCore.matchKakaoLinkAttachments("expected", "committed") { _, _ -> false })

        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.matchKakaoLinkAttachments("expected", "committed") { _, _ -> null }
            }

        assertEquals("bridge core unavailable to match KakaoLink attachments", error.message)
    }

    @Test
    fun `server restart delay dispatch preserves backoff policy`() {
        assertEquals(1_000L, BridgeCore.serverRestartDelayMs(0))
        assertEquals(1_000L, BridgeCore.serverRestartDelayMs(-3))
        assertEquals(1_000L, BridgeCore.serverRestartDelayMs(1))
        assertEquals(2_000L, BridgeCore.serverRestartDelayMs(2))
        assertEquals(4_000L, BridgeCore.serverRestartDelayMs(3))
        assertEquals(30_000L, BridgeCore.serverRestartDelayMs(99))
    }

    @Test
    fun `server restart delay injection preserves native policy value`() {
        assertEquals(
            2_000L,
            BridgeCore.serverRestartDelayMs(
                failureCount = 2,
                restartDelayPolicy = { 2_000L },
            ),
        )
    }

    @Test
    fun `failure metric bucket fails closed when native policy is unavailable`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.failureMetricBucket(
                    errorCode = ImageBridgeProtocol.ERROR_SEND_FAILED,
                    bucketPolicy = { null },
                )
            }

        assertEquals("bridge core unavailable to resolve failure metric bucket", error.message)
    }

    @Test
    fun `failure metric bucket injection preserves native policy value`() {
        assertEquals(
            "timeout",
            BridgeCore.failureMetricBucket(
                errorCode = ImageBridgeProtocol.ERROR_TIMEOUT,
                bucketPolicy = { "timeout" },
            ),
        )
    }

    @Test
    fun `member extraction fails closed before native dispatch when core is unavailable`() {
        var nativeCalled = false

        val error =
            assertFailsWith<IllegalStateException> {
                BridgeCore.memberExtractionEvaluate(
                    containers = emptyList(),
                    expectedMemberHints = emptyList(),
                    preferredPlan = null,
                    loadCompatibleCore = { false },
                    nativeEvaluate = {
                        nativeCalled = true
                        """{"ok":true,"found":false}"""
                    },
                )
            }

        assertEquals("bridge core unavailable to evaluate member extraction", error.message)
        assertFalse(nativeCalled)
    }

    @Test
    fun `member extraction serializes containers and hints into ordered request JSON`() {
        var capturedRequest: String? = null

        val evaluation =
            BridgeCore.memberExtractionEvaluate(
                containers =
                    listOf(
                        MemberExtractionContainerData(
                            path = "$.q",
                            containerType = "collection",
                            views =
                                listOf(
                                    ElementView(
                                        className = "com.kakao.test.Member",
                                        values =
                                            linkedMapOf(
                                                "a" to PrimitiveValue.LongValue(7L),
                                                "b" to PrimitiveValue.StringValue("Alice"),
                                            ),
                                    ),
                                ),
                        ),
                    ),
                expectedMemberHints = listOf(ImageBridgeProtocol.ChatRoomMemberHint(userId = 7L, nickname = "Alice")),
                preferredPlan = null,
                loadCompatibleCore = { true },
                nativeEvaluate = { request ->
                    capturedRequest = request
                    """{"ok":true,"found":false}"""
                },
            )

        assertNull(evaluation)
        val request = JSONObject(assertNotNull(capturedRequest))
        assertEquals(7L, request.getJSONArray("expectedMembers").getJSONObject(0).getLong("userId"))
        val container = request.getJSONArray("containers").getJSONObject(0)
        assertEquals("$.q", container.getString("path"))
        assertEquals("collection", container.getString("containerType"))
        val values =
            container
                .getJSONArray("views")
                .getJSONObject(0)
                .getJSONArray("values")
        assertEquals("a", values.getJSONArray(0).getString(0))
        assertEquals(7L, values.getJSONArray(0).getLong(1))
        assertEquals("b", values.getJSONArray(1).getString(0))
        assertEquals("Alice", values.getJSONArray(1).getString(1))
    }

    @Test
    fun `member extraction parses snapshot envelope and surfaces rejection`() {
        val snapshotEnvelope =
            """
            {"ok":true,"found":true,"snapshot":{
              "sourcePath":"$.q","sourceClassName":"com.kakao.test.Member",
              "members":[{"userId":7,"nickname":"Alice","roleCode":4,"profileImageUrl":null,"mentionUserId":"text-ping-7"}],
              "selectedPlan":{"containerPath":"$.q","sourceClassName":"com.kakao.test.Member","userIdPath":"a","nicknamePath":"b",
                "rolePath":null,"profileImagePath":null,"mentionUserIdPath":null,"fingerprint":"$.q|com.kakao.test.Member|a|b"},
              "confidence":"HIGH","confidenceScore":11000,"usedPreferredPlan":true,"candidateGap":null}}
            """.trimIndent()

        val evaluation =
            assertNotNull(
                BridgeCore.memberExtractionEvaluate(
                    containers = emptyList(),
                    expectedMemberHints = emptyList(),
                    preferredPlan = null,
                    loadCompatibleCore = { true },
                    nativeEvaluate = { snapshotEnvelope },
                ),
            )

        assertEquals("$.q", evaluation.sourcePath)
        assertEquals(listOf(7L), evaluation.members.map { it.userId })
        assertEquals(4, evaluation.members.single().roleCode)
        assertEquals("text-ping-7", evaluation.members.single().mentionUserId)
        assertNull(evaluation.members.single().profileImageUrl)
        assertEquals("$.q|com.kakao.test.Member|a|b", evaluation.selectedPlan.fingerprint)
        assertEquals(ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH, evaluation.confidence)
        assertEquals(true, evaluation.usedPreferredPlan)
        assertNull(evaluation.candidateGap)

        val rejection =
            assertFailsWith<IllegalArgumentException> {
                BridgeCore.memberExtractionEvaluate(
                    containers = emptyList(),
                    expectedMemberHints = emptyList(),
                    preferredPlan = null,
                    loadCompatibleCore = { true },
                    nativeEvaluate = { """{"ok":false,"errorCode":"BAD_REQUEST","error":"unknown containerType: set"}""" },
                )
            }

        assertEquals("unknown containerType: set", rejection.message)
    }

    @Test
    fun `member profile user ids dispatch normalizes ids in Rust`() {
        val userIds =
            BridgeCore.memberProfileUserIds(
                memberIds = listOf(90_001L, 0L, 90_002L, 90_001L),
                memberHints =
                    listOf(
                        ImageBridgeProtocol.ChatRoomMemberHint(userId = 7L),
                        ImageBridgeProtocol.ChatRoomMemberHint(userId = 8L),
                    ),
            )

        assertEquals(listOf(90_001L, 90_002L), userIds)
    }

    @Test
    fun `member profile payload dispatch builds sorted JSON in Rust`() {
        val payload =
            BridgeCore.memberProfilePayloadJson(
                listOf(
                    UpstreamMemberProfile(90_002L, "Member Beta", "https://example.test/p.png"),
                    UpstreamMemberProfile(90_001L, "Member Alpha", null),
                ),
            )
        val members = JSONObject(payload).getJSONArray("members")

        assertEquals(90_001L, members.getJSONObject(0).getLong("userId"))
        assertEquals("Member Alpha", members.getJSONObject(0).getString("nickname"))
        assertFalse(members.getJSONObject(0).has("profileImageUrl"))
        assertEquals(90_002L, members.getJSONObject(1).getLong("userId"))
        assertEquals("https://example.test/p.png", members.getJSONObject(1).getString("profileImageUrl"))
    }

    @Test
    fun `member enrichment missing nicknames skips native dispatch when no inputs exist`() {
        var loadCalled = false
        var nativeCalled = false

        val missing =
            BridgeCore.memberEnrichmentMissingNicknames(
                members = emptyList(),
                expectedMemberHints = emptyList(),
                loadCompatibleCore = {
                    loadCalled = true
                    false
                },
                nativeMissingNicknames = {
                    nativeCalled = true
                    """{"ok":true,"missingUserIds":[]}"""
                },
            )

        assertEquals(emptyList(), missing)
        assertFalse(loadCalled)
        assertFalse(nativeCalled)
    }

    @Test
    fun `member enrichment merge skips native dispatch when upstream profiles are empty`() {
        var loadCalled = false
        var nativeCalled = false
        val snapshot =
            ImageBridgeProtocol.ChatRoomMembersSnapshot(
                roomId = 55L,
                scannedAtEpochMs = 1L,
                sourcePath = "$.members",
                members =
                    listOf(
                        ImageBridgeProtocol.ChatRoomMemberSnapshot(
                            userId = 7L,
                            nickname = "Alice",
                            roleCode = 4,
                            profileImageUrl = "https://example.com/a.png",
                            mentionUserId = "text-ping-7",
                        ),
                    ),
                confidence = ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH,
                confidenceScore = 42,
            )

        val merged =
            BridgeCore.memberEnrichmentMerge(
                snapshot = snapshot,
                upstreamProfiles = emptyList(),
                loadCompatibleCore = {
                    loadCalled = true
                    false
                },
                nativeMerge = {
                    nativeCalled = true
                    """{"ok":true,"members":[],"sourcePath":null,"confidence":"LOW","confidenceScore":0}"""
                },
            )

        assertEquals(snapshot.members, merged.members)
        assertEquals(snapshot.sourcePath, merged.sourcePath)
        assertEquals(snapshot.confidence, merged.confidence)
        assertEquals(snapshot.confidenceScore, merged.confidenceScore)
        assertFalse(loadCalled)
        assertFalse(nativeCalled)
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
    fun `Kakao chat log attachment crypto dispatch preserves legacy golden vector`() {
        val encrypted =
            BridgeCore.encryptKakaoChatLogAttachment(
                encType = 31,
                plaintext = "test",
                userId = 438_562_408L,
            )

        assertEquals("WXFmkb1MZ8akXwAOS8BeOQ==", encrypted)
        assertEquals(
            "test",
            BridgeCore.decryptKakaoChatLogAttachment(
                encType = 31,
                ciphertext = encrypted,
                userId = 438_562_408L,
            ),
        )
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
