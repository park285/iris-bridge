@file:Suppress("ClassName", "FunctionName")

package party.qwer.iris.imagebridge.runtime

import android.net.Credentials
import android.net.LocalSocket
import android.util.Log
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.room.ChatRoomIntentMetadataResolver
import party.qwer.iris.imagebridge.runtime.server.LocalBridgeMuxSocket
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class BridgeErrorLogTest {
    @Before
    fun clearLogs() {
        ShadowLog.clear()
    }

    @Test
    fun `logs KakaoClassRegistry discovery failure and keeps null registry`() {
        val failure = IllegalStateException("registry unavailable")

        val discovery =
            invokeKakaoClassRegistryDiscoveryForBridge {
                throw failure
            }

        assertEquals(null, discovery.registry)
        val log = assertWarningLog("KakaoClassRegistry discovery failed")
        assertSame(failure, log.throwable)
    }

    @Test
    fun `logs peer credentials failure and keeps null peer uid`() {
        val failure = IOException("credentials unavailable")
        val socket = LocalBridgeMuxSocket(ThrowingPeerCredentialsSocket(failure))

        assertEquals(null, socket.peerUid)
        val log = assertWarningLog("Bridge mux peer credentials lookup failed")
        assertSame(failure, log.throwable)
    }

    @Test
    fun `logs intent metadata failure with throwable and keeps null type`() {
        val failure = IllegalStateException("room metadata unavailable")
        val resolver =
            ChatRoomIntentMetadataResolver {
                throw failure
            }

        assertEquals(null, resolver.resolveChatRoomType(123L))
        val log = assertWarningLog("ChatRoom intent metadata resolution failed")
        assertSame(failure, log.throwable)
    }

    private fun invokeKakaoClassRegistryDiscoveryForBridge(discover: () -> KakaoClassRegistry): RegistryDiscoverySnapshot {
        val method =
            Class
                .forName("party.qwer.iris.imagebridge.runtime.IrisBridgeModuleKt")
                .getDeclaredMethod("discoverKakaoClassRegistryForBridge", Function0::class.java)
                .apply { isAccessible = true }
        val result = method.invoke(null, discover)
        val registryField =
            result
                .javaClass
                .getDeclaredField("registry")
                .apply { isAccessible = true }
        return RegistryDiscoverySnapshot(registry = registryField.get(result) as KakaoClassRegistry?)
    }

    private fun assertWarningLog(message: String): ShadowLog.LogItem {
        val log =
            ShadowLog
                .getLogsForTag("IrisBridge")
                .firstOrNull { item ->
                    item.type == Log.WARN &&
                        item.msg.isNotBlank() &&
                        item.msg.contains(message)
                }
        return assertNotNull(log, "expected warning log containing '$message'")
    }

    private class RegistryDiscoverySnapshot(
        val registry: KakaoClassRegistry?,
    )

    private class ThrowingPeerCredentialsSocket(
        private val failure: IOException,
    ) : LocalSocket() {
        override fun getPeerCredentials(): Credentials = throw failure
    }
}
