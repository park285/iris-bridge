package party.qwer.iris

import party.qwer.iris.generated.GeneratedBridgeProtocolContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeneratedBridgeProtocolContractTest {
    @Test
    fun `public protocol constants are generated from Rust bridge core`() {
        assertEquals(GeneratedBridgeProtocolContract.PROTOCOL_VERSION, ImageBridgeProtocol.PROTOCOL_VERSION)
        assertEquals(GeneratedBridgeProtocolContract.MAX_FRAME_SIZE, ImageBridgeProtocol.MAX_FRAME_SIZE)
        assertEquals(GeneratedBridgeProtocolContract.MAX_FRAME_SIZE, LengthPrefixedFrameCodec.MAX_FRAME_SIZE)
        assertEquals(GeneratedBridgeProtocolContract.MUX_VERSION, ImageBridgeMuxProtocol.MUX_VERSION)
        assertEquals(
            GeneratedBridgeProtocolContract.DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME,
            ImageBridgeMuxProtocol.DEFAULT_SOCKET_NAME,
        )
        assertEquals(
            GeneratedBridgeProtocolContract.DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME,
            IrisRuntimePathPolicy.DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME,
        )
        assertEquals(GeneratedBridgeProtocolContract.ACTION_VALUES, BridgeAction.entries.map { it.wireName })
        assertEquals(GeneratedBridgeProtocolContract.THREAT_IDS, BridgeThreat.entries.map { it.id })
    }

    @Test
    fun `generated action specs drive Kotlin bridge capability metadata`() {
        val specsByWireName = GeneratedBridgeProtocolContract.ACTION_SPECS.associateBy { it.wireName }

        for (action in BridgeAction.entries) {
            val generated = assertNotNull(specsByWireName[action.wireName])
            val capability = action.requiredCapability

            assertEquals(action.wireName, generated.wireName)
            assertEquals(capability.id, generated.capabilityId)
            assertEquals(capability.hasSideEffect, generated.hasSideEffect)
            assertEquals(capability.requiresAuthToken, generated.requiresAuthToken)
            assertEquals(capability.threats.map { it.id }.toSet(), generated.threatIds.toSet())
        }
    }

    @Test
    fun `generated capability ids are the Kotlin capability ids`() {
        assertEquals(GeneratedBridgeProtocolContract.CAPABILITY_IDS, BridgeCapability.entries.map { it.id })
    }

    @Test
    fun `generated abi version is the bridge core SSOT value`() {
        assertEquals(40, GeneratedBridgeProtocolContract.ABI_VERSION)
    }
}
