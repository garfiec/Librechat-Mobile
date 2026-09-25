package com.garfiec.librechat.core.model.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Folding tool discovery's `reauth_required` into the connection status (v0.8.8-rc2).
 *
 * The skip rules are the substance: discovery is the OLDER of the two observations, so it must not
 * overwrite a connection that has since authorized — which would tell the user to re-authorize a
 * server that is working.
 */
class McpDiscoveryAuthorizationTest {

    private fun status(
        connectionState: String = "disconnected",
        authorizationState: String? = null,
        generation: String? = null,
    ) = McpServerStatus(
        connectionState = connectionState,
        authorizationState = authorizationState,
        authorizationGeneration = generation,
    )

    private fun reauth(generation: String? = null) = McpServerDiscovery(
        authenticated = false,
        authorizationState = McpAuthorizationStates.REAUTH_REQUIRED,
        authorizationGeneration = generation,
    )

    @Test
    fun aLapsedAuthorizationBecomesNeedsAuthorization() {
        val merged = applyDiscoveryAuthorizationState(
            mapOf("jira" to status(connectionState = "disconnected")),
            mapOf("jira" to reauth()),
        )
        val jira = merged.getValue("jira")
        assertEquals(McpAuthorizationStates.NEEDS_AUTHORIZATION, jira.authorizationState)
        assertEquals("disconnected", jira.connectionState)
        assertEquals(true, jira.requiresOAuth)
    }

    @Test
    fun nothingToApplyReturnsTheSameInstance() {
        // Identity, not equality: callers compare by reference to avoid a pointless state emission.
        val original = mapOf("jira" to status())
        assertSame(original, applyDiscoveryAuthorizationState(original, emptyMap()))
        assertSame(
            original,
            applyDiscoveryAuthorizationState(original, mapOf("jira" to McpServerDiscovery())),
        )
    }

    @Test
    fun aFlowAlreadyRunningIsNotInterrupted() {
        val connecting = mapOf("jira" to status(connectionState = "connecting"))
        assertSame(connecting, applyDiscoveryAuthorizationState(connecting, mapOf("jira" to reauth())))

        val authorizing = mapOf(
            "jira" to status(authorizationState = McpAuthorizationStates.AUTHORIZING),
        )
        assertSame(authorizing, applyDiscoveryAuthorizationState(authorizing, mapOf("jira" to reauth())))
    }

    @Test
    fun anAuthorizedServerIsLeftAloneWhenTheGenerationsCannotBeCompared() {
        // Without both generations there is no way to tell which observation is newer, so the live
        // one wins — the server is connected right now.
        val connected = mapOf("jira" to status(connectionState = "connected"))
        assertSame(connected, applyDiscoveryAuthorizationState(connected, mapOf("jira" to reauth())))

        val connectedWithGen = mapOf("jira" to status(connectionState = "connected", generation = "g1"))
        assertSame(
            connectedWithGen,
            applyDiscoveryAuthorizationState(connectedWithGen, mapOf("jira" to reauth(generation = null))),
        )
    }

    @Test
    fun aStaleVerdictFromAnOlderGenerationIsIgnored() {
        // The credentials discovery complained about have since been replaced.
        val current = mapOf("jira" to status(connectionState = "connected", generation = "g2"))
        assertSame(
            current,
            applyDiscoveryAuthorizationState(current, mapOf("jira" to reauth(generation = "g1"))),
        )
    }

    @Test
    fun anAuthorizedServerOnTheSameGenerationStillLapses() {
        val merged = applyDiscoveryAuthorizationState(
            mapOf("jira" to status(connectionState = "connected", generation = "g1")),
            mapOf("jira" to reauth(generation = "g1")),
        )
        assertEquals(McpAuthorizationStates.NEEDS_AUTHORIZATION, merged.getValue("jira").authorizationState)
    }

    @Test
    fun aServerWithNoStatusYetStillGetsTheVerdict() {
        // The two fetches are independent, so discovery can land first.
        val merged = applyDiscoveryAuthorizationState(emptyMap(), mapOf("jira" to reauth()))
        assertEquals(McpAuthorizationStates.NEEDS_AUTHORIZATION, merged.getValue("jira").authorizationState)
    }

    @Test
    fun otherServersAreUntouched() {
        val merged = applyDiscoveryAuthorizationState(
            mapOf("jira" to status(), "github" to status(connectionState = "connected")),
            mapOf("jira" to reauth(), "github" to McpServerDiscovery()),
        )
        assertEquals("connected", merged.getValue("github").connectionState)
    }
}
