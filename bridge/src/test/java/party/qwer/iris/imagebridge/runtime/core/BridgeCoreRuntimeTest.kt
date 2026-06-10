@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.core

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.ImageBridgeHandshakeProtocol
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BridgeCoreRuntimeTest {
    @Test
    fun `loadOrNull returns runtime whose abi matches and round-trips a context handle`() {
        val runtime = BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true")

        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        try {
            assertEquals(BridgeCore.EXPECTED_ABI_VERSION, BridgeCore.nativeAbiVersion())
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
    fun `handshake hello then client proof round-trips through the native library`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true"),
            )
        try {
            val helloFrame =
                """{"type":"hello","protocolVersion":1,"clientNonce":"client-nonce","socketName":"@iris-image-bridge-mux","timestampMs":1}"""
            val helloEnvelope = BridgeCoreEnvelope.parse(BridgeCore.nativeHandshakeOnHello(runtime.handle, helloFrame, 1_000L, "@iris-image-bridge-mux"))
            assertTrue(helloEnvelope.isOk, "hello must be accepted: ${helloEnvelope.errorMessage}")

            val serverFrameJson = assertNotNull(helloEnvelope.string("frameJson"))
            val serverNonce = assertNotNull(BridgeCoreEnvelope.parse(serverFrameJson).string("serverNonce"))
            val proof =
                ImageBridgeHandshakeProtocol.clientProof(
                    bridgeToken = "bridge-token",
                    clientNonce = "client-nonce",
                    serverNonce = serverNonce,
                )
            val proofFrame = """{"type":"client_proof","protocolVersion":1,"proof":"$proof"}"""
            val proofEnvelope = BridgeCoreEnvelope.parse(BridgeCore.nativeHandshakeOnClientProof(runtime.handle, proofFrame))
            assertTrue(proofEnvelope.isOk, "client proof must authenticate: ${proofEnvelope.errorMessage}")
        } finally {
            runtime.close()
        }
    }
}
