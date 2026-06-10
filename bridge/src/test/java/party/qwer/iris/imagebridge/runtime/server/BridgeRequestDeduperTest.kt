@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime.server

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.DedupeState
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
class BridgeRequestDeduperTest {
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
}
