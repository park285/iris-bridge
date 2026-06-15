package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeCapabilitiesTest {
    @Test
    fun `every protocol action maps to exactly one bridge action`() {
        val protocolActions =
            setOf(
                ImageBridgeProtocol.ACTION_SEND_IMAGE,
                ImageBridgeProtocol.ACTION_SEND_TEXT,
                ImageBridgeProtocol.ACTION_SEND_MARKDOWN,
                ImageBridgeProtocol.ACTION_HEALTH,
                ImageBridgeProtocol.ACTION_INSPECT_CHATROOM,
                ImageBridgeProtocol.ACTION_OPEN_CHATROOM,
                ImageBridgeProtocol.ACTION_MARK_CHATROOM_READ,
                ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS,
                ImageBridgeProtocol.ACTION_FETCH_MEMBER_PROFILES,
            )
        val mappedWireNames = BridgeAction.entries.map { it.wireName }.toSet()
        assertEquals(protocolActions, mappedWireNames)
    }

    @Test
    fun `every bridge action resolves from its wire name`() {
        for (action in BridgeAction.entries) {
            assertEquals(action, BridgeAction.fromWireName(action.wireName))
        }
    }

    @Test
    fun `unknown wire name does not resolve to an action`() {
        assertEquals(null, BridgeAction.fromWireName("definitely_not_an_action"))
    }

    @Test
    fun `every capability is required by at least one action`() {
        val requiredCapabilities = BridgeAction.entries.map { it.requiredCapability }.toSet()
        assertEquals(BridgeCapability.entries.toSet(), requiredCapabilities)
    }

    @Test
    fun `every capability carries a non-blank identifier and description`() {
        for (capability in BridgeCapability.entries) {
            assertTrue(capability.id.isNotBlank(), "capability id must not be blank: $capability")
            assertTrue(
                capability.description.isNotBlank(),
                "capability description must not be blank: $capability",
            )
        }
    }

    @Test
    fun `capability ids are unique`() {
        val ids = BridgeCapability.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `side effect actions are exactly the send and open actions`() {
        val sideEffectWireNames =
            BridgeAction.entries
                .filter { it.requiredCapability.hasSideEffect }
                .map { it.wireName }
                .toSet()
        assertEquals(
            setOf(
                ImageBridgeProtocol.ACTION_SEND_IMAGE,
                ImageBridgeProtocol.ACTION_SEND_TEXT,
                ImageBridgeProtocol.ACTION_SEND_MARKDOWN,
                ImageBridgeProtocol.ACTION_OPEN_CHATROOM,
                ImageBridgeProtocol.ACTION_MARK_CHATROOM_READ,
            ),
            sideEffectWireNames,
        )
    }

    @Test
    fun `read only actions are exactly health inspect snapshot and member profile fetch`() {
        val readOnlyWireNames =
            BridgeAction.entries
                .filterNot { it.requiredCapability.hasSideEffect }
                .map { it.wireName }
                .toSet()
        assertEquals(
            setOf(
                ImageBridgeProtocol.ACTION_HEALTH,
                ImageBridgeProtocol.ACTION_INSPECT_CHATROOM,
                ImageBridgeProtocol.ACTION_SNAPSHOT_CHATROOM_MEMBERS,
                ImageBridgeProtocol.ACTION_FETCH_MEMBER_PROFILES,
            ),
            readOnlyWireNames,
        )
    }

    @Test
    fun `only health is exempt from the bridge auth token`() {
        val tokenExemptWireNames =
            BridgeAction.entries
                .filterNot { it.requiredCapability.requiresAuthToken }
                .map { it.wireName }
                .toSet()
        assertEquals(setOf(ImageBridgeProtocol.ACTION_HEALTH), tokenExemptWireNames)
    }

    @Test
    fun `every capability declares at least one threat`() {
        for (capability in BridgeCapability.entries) {
            assertTrue(
                capability.threats.isNotEmpty(),
                "capability must enumerate its threats: $capability",
            )
            for (threat in capability.threats) {
                assertNotNull(threat)
            }
        }
    }

    @Test
    fun `capability matrix lists one row per protocol action`() {
        val rows = BridgeCapabilityMatrix.rows
        assertEquals(BridgeAction.entries.size, rows.size)
        assertEquals(BridgeAction.entries.toSet(), rows.map { it.action }.toSet())
        for (row in rows) {
            assertEquals(row.action.requiredCapability, row.capability)
            assertEquals(row.action.requiredCapability.threats, row.threats)
        }
    }
}
