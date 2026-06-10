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
    fun `handshake hello then client proof round-trips through the native library`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true"),
            )
        try {
            val helloFrame =
                """{"type":"hello","protocolVersion":1,"clientNonce":"client-nonce","socketName":"@iris-image-bridge-mux","timestampMs":1}"""
            val helloEnvelope = BridgeCoreEnvelope.parse(BridgeCore.nativeHandshakeOnHello(runtime.handle, helloFrame, 1_000L))
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
