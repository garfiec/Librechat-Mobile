package com.garfiec.librechat.core.model.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpOAuthServerNameTest {

    @Test
    fun redirectUriNamesTheStoredServer() {
        val url = "https://idp/authorize?redirect_uri=" +
            "https%3A%2F%2Fhost%2Flibrechat%2Fapi%2Fmcp%2FGit%2BHub%2520Tools%2Foauth%2Fcallback&x=1"
        // Decoded twice, as the server parses it: once as a query value, once as a path segment,
        // where an escaped `+` stays literal.
        assertEquals("Git+Hub Tools", mcpOAuthServerName("oauth_mcp_Git_Hub_Tools", url))
    }

    @Test
    fun toolNameIsTheFallback() {
        assertEquals("github", mcpOAuthServerName("oauth_mcp_github", "https://idp/authorize?state=s"))
        assertEquals("github", mcpOAuthServerName("oauth_mcp_github", null))
    }

    @Test
    fun anythingElseIsNoServer() {
        val actionUrl = "https://idp/authorize?redirect_uri=https%3A%2F%2Fhost%2Fapi%2Factions%2Fa1%2Foauth%2Fcallback"
        assertNull(mcpOAuthServerName("search", actionUrl))
        assertNull(mcpOAuthServerName("oauth_mcp_", null))
        assertNull(mcpOAuthServerName(null, null))
    }
}
