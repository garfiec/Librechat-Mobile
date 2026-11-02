package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The v0.8.9-rc1 `interface.*` additions and the readers that apply their defaults. */
class InterfaceConfigV089Test {

    private val json = Json { ignoreUnknownKeys = true }

    private fun iface(body: String) = json.decodeFromString(InterfaceConfig.serializer(), body)

    private fun startup(body: String) = json.decodeFromString(StartupConfig.serializer(), body)

    @Test
    fun reconciliationWindowDefaultsWhenAbsent() {
        assertEquals(60_000, iface("{}").queuedTurnReconciliationTimeoutMs())
        assertEquals(60_000, (null as InterfaceConfig?).queuedTurnReconciliationTimeoutMs())
    }

    @Test
    fun reconciliationWindowHonoursAValidValue() {
        assertEquals(180_000, iface("""{ "queuedTurnReconciliationTimeoutMs": 180000 }""").queuedTurnReconciliationTimeoutMs())
    }

    @Test
    fun reconciliationWindowFallsBackOnAnUnvalidatedValue() {
        listOf("0", "-5", "1.5", "\"90000\"", "2147483648", "null", "{}").forEach { raw ->
            assertEquals(
                60_000,
                iface("""{ "queuedTurnReconciliationTimeoutMs": $raw }""").queuedTurnReconciliationTimeoutMs(),
                raw,
            )
        }
    }

    @Test
    fun steerArmWindowDefaultsWhenAbsent() {
        assertEquals(10_000, iface("{}").steerArmConfirmationTimeoutMs())
        assertEquals(10_000, (null as InterfaceConfig?).steerArmConfirmationTimeoutMs())
    }

    @Test
    fun steerArmWindowHonoursAValidValue() {
        assertEquals(4_000, iface("""{ "steerArmConfirmationTimeoutMs": 4000 }""").steerArmConfirmationTimeoutMs())
        assertEquals(
            2_147_483_647,
            iface("""{ "steerArmConfirmationTimeoutMs": 2147483647 }""").steerArmConfirmationTimeoutMs(),
        )
    }

    @Test
    fun steerArmWindowFallsBackOnAnUnvalidatedValue() {
        listOf("0", "-5", "1.5", "\"9000\"", "2147483648", "null", "{}").forEach { raw ->
            assertEquals(
                10_000,
                iface("""{ "steerArmConfirmationTimeoutMs": $raw }""").steerArmConfirmationTimeoutMs(),
                raw,
            )
        }
    }

    @Test
    fun onlyEphemeralRetentionForcesTemporaryChats() {
        assertTrue(isForcedTemporaryRetention("ephemeral"))
        assertFalse(isForcedTemporaryRetention("all"))
        assertFalse(isForcedTemporaryRetention("temporary"))
        assertFalse(isForcedTemporaryRetention(null))
    }

    @Test
    fun onlyGreetingSwapsTheLandingHeading() {
        assertTrue(iface("""{ "landingHeading": "greeting" }""").landingHeadingIsGreeting())
        assertFalse(iface("""{ "landingHeading": "entity" }""").landingHeadingIsGreeting())
        assertFalse(iface("{}").landingHeadingIsGreeting())
        assertFalse((null as InterfaceConfig?).landingHeadingIsGreeting())
    }

    @Test
    fun titleOwnershipNeedsTheCapabilityAndRunningRename() {
        val on = """{ "conversationTitleOwnershipVersion": 1, "interface": { "runningChatRename": true } }"""
        assertTrue(startup(on).supportsConversationTitleOwnership)
        // The schema defaults runningChatRename to true, so an absent flag is on.
        assertTrue(startup("""{ "conversationTitleOwnershipVersion": 1 }""").supportsConversationTitleOwnership)
        val off = """{ "conversationTitleOwnershipVersion": 1, "interface": { "runningChatRename": false } }"""
        assertFalse(startup(off).supportsConversationTitleOwnership)
        assertFalse(startup("""{ "interface": { "runningChatRename": true } }""").supportsConversationTitleOwnership)
        assertFalse(startup("""{ "conversationTitleOwnershipVersion": 2 }""").supportsConversationTitleOwnership)
    }
}
