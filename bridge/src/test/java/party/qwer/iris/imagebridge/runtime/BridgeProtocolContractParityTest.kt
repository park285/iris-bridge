package party.qwer.iris.imagebridge.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.BridgeAction
import party.qwer.iris.BridgeCapability
import party.qwer.iris.BridgeThreat
import party.qwer.iris.ImageBridgeHandshakeProtocol
import party.qwer.iris.ImageBridgeMuxProtocol
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.LengthPrefixedFrameCodec
import party.qwer.iris.generated.GeneratedBridgeProtocolContract
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.BridgeCoreJniContext
import party.qwer.iris.imagebridge.runtime.core.loadOrNull
import party.qwer.iris.imagebridge.runtime.core.protocolContractJson
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class BridgeProtocolContractParityTest {
    @Test
    fun `Rust contract matches Kotlin protocol constants`() {
        val contract = rustBridgeProtocolContract()

        assertEquals(expectedContractKeys(), contract.keys().asSequence().toSet())
        assertEquals(ImageBridgeProtocol.PROTOCOL_VERSION, contract.getInt("protocolVersion"))
        assertEquals(ImageBridgeProtocol.MAX_FRAME_SIZE, contract.getInt("maxFrameSize"))
        assertEquals(LengthPrefixedFrameCodec.MAX_FRAME_SIZE, contract.getInt("maxFrameSize"))
        assertEquals(ImageBridgeMuxProtocol.MUX_VERSION, contract.getInt("muxVersion"))
        assertEquals(
            ImageBridgeMuxProtocol.DEFAULT_SOCKET_NAME,
            contract.getString("defaultImageBridgeMuxSocketName"),
        )
        assertEquals(expectedActionWireNames(), contract.getJSONArray("actions").objects().map { it.getString("wireName") })
        assertEquals(expectedStatuses(), contract.getJSONArray("statuses").strings())
        assertEquals(expectedErrors(), contract.getJSONArray("errors").strings())
        assertEquals(expectedMuxFrameTypes(), contract.getJSONArray("muxFrameTypes").strings())
        assertEquals(expectedHandshakeFrameTypes(), contract.getJSONArray("handshakeFrameTypes").strings())
        assertEquals(BridgeThreat.entries.map { it.id }, contract.getJSONArray("threats").strings())
    }

    @Test
    fun `Rust action specs match Kotlin bridge capability metadata`() {
        val contractActions =
            rustBridgeProtocolContract()
                .getJSONArray("actions")
                .objects()
                .associateBy { it.getString("wireName") }

        assertEquals(BridgeAction.entries.map { it.wireName }.toSet(), contractActions.keys)
        assertEquals(
            BridgeCapability.entries.map { it.id }.toSet(),
            contractActions.values.map { it.getString("capabilityId") }.toSet(),
        )

        for (action in BridgeAction.entries) {
            val contractAction = assertNotNull(contractActions[action.wireName])
            val capability = action.requiredCapability
            assertEquals(action.wireName, contractAction.getString("wireName"))
            assertEquals(capability.id, contractAction.getString("capabilityId"))
            assertEquals(capability.hasSideEffect, contractAction.getBoolean("hasSideEffect"))
            assertEquals(capability.requiresAuthToken, contractAction.getBoolean("requiresAuthToken"))
            assertEquals(capability.threats.map { it.id }.toSet(), contractAction.getJSONArray("threatIds").strings().toSet())
        }
    }

    @Test
    fun `native abi version matches generated contract and bridge core expectation`() {
        val runtime =
            assertNotNull(
                BridgeCore.loadOrNull(securityMode = "production", bridgeToken = "bridge-token", requireHandshakeRaw = "true"),
                "host .so must load via java.library.path in unit tests",
            )
        try {
            assertEquals(GeneratedBridgeProtocolContract.ABI_VERSION, BridgeCore.EXPECTED_ABI_VERSION)
            assertEquals(GeneratedBridgeProtocolContract.ABI_VERSION, BridgeCoreJniContext.nativeAbiVersion())
        } finally {
            runtime.close()
        }
    }

    private fun rustBridgeProtocolContract(): JSONObject = JSONObject(assertNotNull(BridgeCore.protocolContractJson()))

    private fun expectedContractKeys(): Set<String> =
        setOf(
            "actions",
            "defaultImageBridgeMuxSocketName",
            "errors",
            "handshakeFrameTypes",
            "maxFrameSize",
            "muxFrameTypes",
            "muxVersion",
            "protocolVersion",
            "statuses",
            "threats",
        )

    private fun expectedActionWireNames(): List<String> =
        listOf(
            ImageBridgeProtocol.ACTION_SEND_IMAGE,
            ImageBridgeProtocol.ACTION_SEND_TEXT,
            ImageBridgeProtocol.ACTION_SEND_MARKDOWN,
            ImageBridgeProtocol.ACTION_OPEN_CHATROOM,
            ImageBridgeProtocol.ACTION_MARK_CHATROOM_READ,
            ImageBridgeProtocol.ACTION_INSPECT_CHATROOM,
            ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS,
            ImageBridgeProtocol.ACTION_FETCH_MEMBER_PROFILES,
            ImageBridgeProtocol.ACTION_HEALTH,
        )

    private fun expectedStatuses(): List<String> =
        listOf(
            ImageBridgeProtocol.STATUS_SENT,
            ImageBridgeProtocol.STATUS_FAILED,
            ImageBridgeProtocol.STATUS_OK,
        )

    private fun expectedErrors(): List<String> =
        listOf(
            ImageBridgeProtocol.ERROR_UNSUPPORTED_PROTOCOL,
            ImageBridgeProtocol.ERROR_UNAUTHORIZED,
            ImageBridgeProtocol.ERROR_BAD_REQUEST,
            ImageBridgeProtocol.ERROR_PATH_VALIDATION,
            ImageBridgeProtocol.ERROR_BRIDGE_BUSY,
            ImageBridgeProtocol.ERROR_BRIDGE_SHUTTING_DOWN,
            ImageBridgeProtocol.ERROR_SEND_FAILED,
            ImageBridgeProtocol.ERROR_TIMEOUT,
            ImageBridgeProtocol.ERROR_INTERNAL,
            ImageBridgeProtocol.ERROR_MISSING_REQUEST_ID,
            ImageBridgeProtocol.ERROR_DUPLICATE_REQUEST,
            ImageBridgeProtocol.ERROR_CANCELLED,
        )

    private fun expectedMuxFrameTypes(): List<String> =
        listOf(
            ImageBridgeMuxProtocol.TYPE_REQUEST,
            ImageBridgeMuxProtocol.TYPE_RESPONSE,
            ImageBridgeMuxProtocol.TYPE_PING,
            ImageBridgeMuxProtocol.TYPE_PONG,
            ImageBridgeMuxProtocol.TYPE_CANCEL,
            ImageBridgeMuxProtocol.TYPE_GOAWAY,
        )

    private fun expectedHandshakeFrameTypes(): List<String> =
        listOf(
            ImageBridgeHandshakeProtocol.TYPE_HELLO,
            ImageBridgeHandshakeProtocol.TYPE_SERVER_PROOF,
            ImageBridgeHandshakeProtocol.TYPE_CLIENT_PROOF,
        )

    private fun JSONArray.strings(): List<String> = List(length()) { index -> getString(index) }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { index -> getJSONObject(index) }
}
