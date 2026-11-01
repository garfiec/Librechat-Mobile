package com.garfiec.librechat.core.ui.mcp

import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpOAuthNavigationGateTest {

    private val authUrl =
        "https://idp.example.com/authorize?client_id=c&redirect_uri=https%3A%2F%2Fapi.example.com%2Fapi%2Fmcp%2Fgithub%2Foauth%2Fcallback"
    private val gate = McpOAuthNavigationGate("https://chat.example.com/librechat", authUrl)

    @Test
    fun providerPagesAndTheCallbackItselfProceed() {
        assertNull(gate.decide(authUrl))
        assertNull(gate.decide("https://idp.example.com/login?next=/oauth/success"))
        assertNull(gate.decide("https://api.example.com/api/mcp/github/oauth/callback?code=x&state=y"))
    }

    @Test
    fun successOnEitherHostEndsTheFlow() {
        assertEquals(McpOAuthOutcome.Success, gate.decide("https://chat.example.com/librechat/oauth/success?serverName=github"))
        assertEquals(McpOAuthOutcome.Success, gate.decide("https://API.example.com/oauth/success/?serverName=github"))
    }

    @Test
    fun errorCarriesItsCode() {
        assertEquals(
            McpOAuthOutcome.Error("csrf_validation_failed"),
            gate.decide("https://api.example.com/oauth/error?error=csrf_validation_failed"),
        )
        assertEquals(McpOAuthOutcome.Error(null), gate.decide("https://chat.example.com/oauth/error"))
    }

    @Test
    fun cookiesGoToTheCallbackOriginAndTheAppsServer() {
        assertEquals(
            listOf("https://api.example.com", "https://chat.example.com"),
            mcpOAuthCookieOrigins("https://chat.example.com/librechat", authUrl),
        )
        assertEquals(
            listOf("http://localhost:3080"),
            mcpOAuthCookieOrigins(
                "http://localhost:3080",
                "https://idp/a?redirect_uri=http%3A%2F%2Flocalhost%3A3080%2Fapi%2Fmcp%2Fx%2Foauth%2Fcallback",
            ),
        )
    }

    @Test
    fun aProviderPathOnItsOwnHostNeverMatches() {
        assertNull(gate.decide("https://idp.example.com/oauth/error?error=access_denied"))
        assertNull(gate.decide("https://idp.example.com/oauth/success"))
    }
}
