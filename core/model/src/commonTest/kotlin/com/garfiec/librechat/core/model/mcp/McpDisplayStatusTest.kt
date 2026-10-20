package com.garfiec.librechat.core.model.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpDisplayStatusTest {

    private val server = McpServer(name = "docs")
    private val scoped = server.copy(requestScoped = true)

    private fun status(server: McpServer = this.server, isReinitializing: Boolean = false, status: McpServerStatus?) =
        mcpDisplayStatus(server, status, isReinitializing)

    @Test
    fun connected() {
        assertEquals(McpDisplayStatus.CONNECTED, status(status = McpServerStatus(connectionState = "connected")))
    }

    @Test
    fun a_reconnect_in_flight_reads_as_connecting_whatever_the_last_status() {
        assertEquals(
            McpDisplayStatus.CONNECTING,
            status(isReinitializing = true, status = McpServerStatus(connectionState = "error")),
        )
        assertEquals(McpDisplayStatus.CONNECTING, status(status = McpServerStatus(connectionState = "connecting")))
        assertEquals(
            McpDisplayStatus.CONNECTING,
            status(status = McpServerStatus(authorizationState = McpAuthorizationStates.AUTHORIZING)),
        )
    }

    @Test
    fun oauth_required_and_needs_authorization_both_ask_for_sign_in() {
        assertEquals(McpDisplayStatus.NEEDS_SIGN_IN, status(status = McpServerStatus(requiresOAuth = true)))
        assertEquals(
            McpDisplayStatus.NEEDS_SIGN_IN,
            status(status = McpServerStatus(authorizationState = McpAuthorizationStates.NEEDS_AUTHORIZATION)),
        )
    }

    /** Discovery's reauth verdict is folded in before mapping, so a lapsed grant asks for sign-in. */
    @Test
    fun a_discovery_reauth_verdict_asks_for_sign_in() {
        val folded = applyDiscoveryAuthorizationState(
            mapOf("docs" to McpServerStatus(connectionState = "disconnected")),
            mapOf("docs" to McpServerDiscovery(authorizationState = McpAuthorizationStates.REAUTH_REQUIRED)),
        )
        assertEquals(McpDisplayStatus.NEEDS_SIGN_IN, status(status = folded["docs"]))
    }

    @Test
    fun missing_user_variables_need_setup() {
        assertEquals(
            McpDisplayStatus.NEEDS_SETUP,
            status(status = McpServerStatus(configurationState = McpConfigurationStates.NEEDS_CONFIGURATION)),
        )
    }

    @Test
    fun a_request_scoped_server_idle_between_chats_is_not_a_fault() {
        assertEquals(McpDisplayStatus.ON_DEMAND, status(status = McpServerStatus(requestScoped = true)))
        assertEquals(McpDisplayStatus.ON_DEMAND, status(server = scoped, status = McpServerStatus()))
        assertEquals(McpDisplayStatus.ON_DEMAND, status(server = scoped, status = null))
    }

    @Test
    fun a_request_scoped_server_that_connected_says_so() {
        assertEquals(
            McpDisplayStatus.CONNECTED,
            status(server = scoped, status = McpServerStatus(connectionState = "connected", requestScoped = true)),
        )
    }

    @Test
    fun an_error_state_or_message_is_a_failure() {
        assertEquals(McpDisplayStatus.FAILED, status(status = McpServerStatus(connectionState = "error")))
        assertEquals(McpDisplayStatus.FAILED, status(status = McpServerStatus(error = "ECONNREFUSED")))
        // Upstream paints an errored OAuth server red; requiresOAuth only matters while disconnected.
        assertEquals(
            McpDisplayStatus.FAILED,
            status(status = McpServerStatus(connectionState = "error", requiresOAuth = true)),
        )
    }

    @Test
    fun plain_disconnected_and_unreported() {
        assertEquals(McpDisplayStatus.DISCONNECTED, status(status = McpServerStatus()))
        assertEquals(McpDisplayStatus.UNKNOWN, status(status = null))
    }

    @Test
    fun only_states_fixable_from_the_list_need_attention() {
        assertTrue(McpDisplayStatus.NEEDS_SIGN_IN.needsAttention)
        assertTrue(McpDisplayStatus.FAILED.needsAttention)
        assertFalse(McpDisplayStatus.ON_DEMAND.needsAttention)
        assertFalse(McpDisplayStatus.NEEDS_SETUP.needsAttention)
    }
}
