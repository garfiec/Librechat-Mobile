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
    fun plainHttpProviderPagesAreRefused() {
        val insecure = McpOAuthOutcome.Error(McpOAuthNavigationGate.INSECURE_CONNECTION)
        assertEquals(insecure, gate.decide("http://idp.example.com/login"))
        assertEquals(insecure, gate.decide("HTTP://evil.example.net/authorize?x=1"))
        assertEquals(insecure, gate.decide("http://192.168.1.20:8080/realms/r/protocol/openid-connect/auth"))
        assertEquals(insecure, gate.decide("http://127.0.0.1.evil.example/"))
        assertNull(gate.decide("https://idp.example.com/login"))
    }

    @Test
    fun plainHttpIsAllowedOnTheServersHostsAndLoopback() {
        val lan = McpOAuthNavigationGate(
            "http://192.168.1.10:3080",
            "https://idp/a?redirect_uri=http%3A%2F%2F192.168.1.10%3A3080%2Fapi%2Fmcp%2Fx%2Foauth%2Fcallback",
        )
        assertNull(lan.decide("http://192.168.1.10:3080/api/mcp/x/oauth/callback?code=c&state=s"))
        assertEquals(McpOAuthOutcome.Success, lan.decide("http://192.168.1.10:3080/oauth/success?serverName=x"))

        assertNull(gate.decide("http://localhost:8080/authorize"))
        assertNull(gate.decide("http://auth.localhost/authorize"))
        assertNull(gate.decide("http://127.0.0.1:9000/authorize"))
        assertNull(gate.decide("http://[::1]:9000/authorize"))
    }

    @Test
    fun plainHttpIsExemptOnlyOnTheServersOwnOrigin() {
        val insecure = McpOAuthOutcome.Error(McpOAuthNavigationGate.INSECURE_CONNECTION)
        // An HTTPS server's host over plain HTTP is not the server the user accepted.
        assertEquals(insecure, gate.decide("http://api.example.com/api/mcp/github/oauth/callback?code=x"))
        assertEquals(insecure, gate.decide("http://chat.example.com/librechat/login"))
        val lan = McpOAuthNavigationGate("http://192.168.1.10:3080", "https://idp/a")
        // Another port on the same host is another origin.
        assertEquals(insecure, lan.decide("http://192.168.1.10:8080/login"))
        assertEquals(insecure, lan.decide("http://192.168.1.10/login"))
        val defaultPort = McpOAuthNavigationGate("http://chat.lan", "https://idp/a")
        assertNull(defaultPort.decide("http://chat.lan:80/api/mcp/x/oauth/callback"))
    }

    @Test
    fun pageLocationIsTheHostAndWhetherItIsHttps() {
        assertEquals(
            McpOAuthPageLocation("idp.example.com", secure = true),
            mcpOAuthPageLocation("https://user@IDP.example.com:443/login?a=b"),
        )
        assertEquals(McpOAuthPageLocation("localhost", secure = false), mcpOAuthPageLocation("http://localhost:3080/"))
        assertNull(mcpOAuthPageLocation("about:blank"))
    }

    @Test
    fun aProviderPathOnItsOwnHostNeverMatches() {
        assertNull(gate.decide("https://idp.example.com/oauth/error?error=access_denied"))
        assertNull(gate.decide("https://idp.example.com/oauth/success"))
    }
}
