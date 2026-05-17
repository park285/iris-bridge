@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.discovery.BridgeDiscoverySnapshot
import party.qwer.iris.imagebridge.runtime.discovery.DiscoveryHookStatus
import party.qwer.iris.imagebridge.runtime.discovery.HOOK_SEND_MULTIPLE
import party.qwer.iris.imagebridge.runtime.server.BridgeSpecCheck
import party.qwer.iris.imagebridge.runtime.server.BridgeSpecStatus
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitiesSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeCapabilitySnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeHealthSnapshot
import party.qwer.iris.imagebridge.runtime.server.ImageBridgeRequestHandler
import party.qwer.iris.imagebridge.runtime.server.toJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageBridgeRequestHandlerHealthTest {
    @Test
    fun `health action returns spec snapshot`() {
        val healthSnapshot =
            ImageBridgeHealthSnapshot(
                running = true,
                specStatus =
                    BridgeSpecStatus(
                        ready = false,
                        checkedAtEpochMs = 1234L,
                        checks = listOf(BridgeSpecCheck(name = "class bh.c", ok = false, detail = "missing")),
                    ),
                discoverySnapshot =
                    BridgeDiscoverySnapshot(
                        installAttempted = true,
                        hooks =
                            listOf(
                                DiscoveryHookStatus(
                                    name = HOOK_SEND_MULTIPLE,
                                    installed = true,
                                    invocationCount = 4,
                                    lastSeenEpochMs = 99L,
                                    lastSummary = "uris=2",
                                ),
                            ),
                    ),
                capabilities =
                    ImageBridgeCapabilitiesSnapshot(
                        inspectChatRoom = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                        openChatRoom = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                        snapshotChatRoomMembers = ImageBridgeCapabilitySnapshot(supported = true, ready = true),
                        sendText =
                            ImageBridgeCapabilitySnapshot(
                                supported = false,
                                ready = false,
                                reason = "text sender unavailable",
                            ),
                        sendMarkdown =
                            ImageBridgeCapabilitySnapshot(
                                supported = false,
                                ready = false,
                                reason = "text sender unavailable",
                            ),
                    ),
                restartCount = 3,
                lastCrashMessage = "bind failed",
            )
        val handler =
            ImageBridgeRequestHandler(
                imageSender = { error("should not be called") },
                healthProvider = { healthSnapshot },
                handshakeValidator = developmentHandshakeValidator(),
            )

        val response =
            handler.handle(
                healthRequest(),
            )

        assertEquals(party.qwer.iris.ImageBridgeProtocol.STATUS_OK, response.status)
        assertFalse(response.specReady == true)
        assertEquals(3, response.restartCount)
        assertEquals("bind failed", response.lastCrashMessage)
        assertEquals(1, response.checks.size)
        assertEquals(1, response.discovery?.hooks?.size)
        assertTrue(response.capabilities?.openChatRoom?.ready == true)
        assertTrue(response.capabilities?.snapshotChatRoomMembers?.ready == true)
        assertFalse(response.capabilities?.sendText?.supported == true)
        assertEquals("text sender unavailable", response.capabilities?.sendText?.reason)
        val jsonCapabilities = healthSnapshot.toJson().getJSONObject("capabilities")
        assertFalse(jsonCapabilities.getJSONObject("sendText").getBoolean("supported"))
        assertFalse(jsonCapabilities.getJSONObject("sendMarkdown").getBoolean("ready"))
        assertFalse(jsonCapabilities.has("karingAot"))
    }
}
