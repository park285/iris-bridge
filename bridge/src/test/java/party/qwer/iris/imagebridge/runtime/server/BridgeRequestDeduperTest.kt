@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.DedupeState
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
class BridgeRequestDeduperTest {
    @Test
    fun `completed response is stored in the core ledger for cached replay`() {
        val runtime =
            BridgeCore.loadOrNull(
                securityMode = "development",
                bridgeToken = "bridge-token",
                requireHandshakeRaw = null,
            )
        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        runtime.use { core ->
            val deduper = BridgeRequestDeduper(bridgeCore = core, nowMs = { 1_000L })
            val key = "send_text:req-store-response"

            val response =
                deduper.execute(key, requestId = "req-store-response") {
                    ImageBridgeProtocol.ImageBridgeResponse(
                        status = ImageBridgeProtocol.STATUS_OK,
                        requestId = "req-store-response",
                    )
                }
            assertEquals(ImageBridgeProtocol.STATUS_OK, response.status)

            val state = core.dedupeAdmit(key, 1_001L).dedupeState()
            assertTrue(state is DedupeState.Cached, "completed request must become cached, got $state")
            val responseJson = JSONObject(state.responseJson ?: fail("cached responseJson missing"))
            assertEquals(ImageBridgeProtocol.STATUS_OK, responseJson.getString("status"))
            assertEquals("req-store-response", responseJson.getString("requestId"))
        }
    }

    @Test
    fun `cached core dedupe verdict replays stored response without running block`() {
        val runtime =
            BridgeCore.loadOrNull(
                securityMode = "development",
                bridgeToken = "bridge-token",
                requireHandshakeRaw = null,
            )
        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        runtime.use { core ->
            val deduper = BridgeRequestDeduper(bridgeCore = core, nowMs = { 2_000L })
            val key = "send_text:req-replay"
            assertTrue(core.dedupeAdmit(key, 1_000L).dedupeState() is DedupeState.Fresh)
            core.dedupeComplete(
                key,
                """{"status":"ok","requestId":"req-replay","payloadJson":"{\"sent\":true}"}""",
                1_001L,
            )

            var executed = false
            val response =
                deduper.execute(key, requestId = "req-replay") {
                    executed = true
                    fail("block must not run")
                }

            assertFalse(executed, "cached Rust dedupe verdict must stop duplicate side effects")
            assertEquals(ImageBridgeProtocol.STATUS_OK, response.status)
            assertEquals("req-replay", response.requestId)
            assertEquals("""{"sent":true}""", response.payloadJson)
        }
    }

    @Test
    fun `failed execution also records completion in the core ledger`() {
        val runtime =
            BridgeCore.loadOrNull(
                securityMode = "development",
                bridgeToken = "bridge-token",
                requireHandshakeRaw = null,
            )
        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        runtime.use { core ->
            val deduper = BridgeRequestDeduper(bridgeCore = core, nowMs = { 1_000L })

            try {
                deduper.execute("send:req-fail") { error("boom") }
                fail("execute must rethrow the block failure")
            } catch (expected: IllegalStateException) {
                // 실패 응답도 future에 캐시되는 기존 의미론 — core ledger도 동일하게 완료돼야 한다.
            }

            val state = core.dedupeAdmit("send:req-fail", 1_001L).dedupeState()
            assertTrue(
                state is DedupeState.Cached,
                "core ledger must be completed even when the block fails, got $state",
            )
        }
    }

    @Test
    fun `cached core dedupe verdict does not re-run side effect block`() {
        val runtime =
            BridgeCore.loadOrNull(
                securityMode = "development",
                bridgeToken = "bridge-token",
                requireHandshakeRaw = null,
            )
        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        runtime.use { core ->
            val deduper = BridgeRequestDeduper(bridgeCore = core, nowMs = { 2_000L })
            val key = "send_text:req-duplicate"
            assertTrue(core.dedupeAdmit(key, 1_000L).dedupeState() is DedupeState.Fresh)
            core.dedupeComplete(key, "{}", 1_001L)

            var executed = false
            val response =
                deduper.execute(key, requestId = "req-duplicate") {
                    executed = true
                    ImageBridgeProtocol.ImageBridgeResponse(status = ImageBridgeProtocol.STATUS_OK)
                }

            assertFalse(executed, "cached Rust dedupe verdict must stop duplicate side effects")
            assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
            assertEquals(ImageBridgeProtocol.ERROR_DUPLICATE_REQUEST, response.errorCode)
            assertEquals("req-duplicate", response.requestId)
        }
    }

    @Test
    fun `in flight core dedupe verdict fails busy without running block`() {
        val runtime =
            BridgeCore.loadOrNull(
                securityMode = "development",
                bridgeToken = "bridge-token",
                requireHandshakeRaw = null,
            )
        assertNotNull(runtime, "host .so must load via java.library.path in unit tests")
        runtime.use { core ->
            val deduper = BridgeRequestDeduper(bridgeCore = core, nowMs = { 2_000L })
            val key = "send_text:req-in-flight"
            assertTrue(core.dedupeAdmit(key, 1_000L).dedupeState() is DedupeState.Fresh)

            var executed = false
            val response =
                deduper.execute(key, requestId = "req-in-flight") {
                    executed = true
                    fail("block must not run")
                }

            assertFalse(executed, "in-flight Rust dedupe verdict must stop duplicate side effects")
            assertEquals(ImageBridgeProtocol.STATUS_FAILED, response.status)
            assertEquals(ImageBridgeProtocol.ERROR_BRIDGE_BUSY, response.errorCode)
            assertEquals("req-in-flight", response.requestId)
        }
    }
}
