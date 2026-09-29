package com.garfiec.librechat.feature.settings.screen

import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the MCP server dialog sends for an API key, mirroring upstream `useMCPServerForm`.
 *
 * The update route replaces the config and reads never return the key, so the dialog has to resend
 * the stored source: resending `user` over an admin key is a 200 that throws the shared key away.
 */
class McpApiKeyFormTest {

    private fun server(source: McpApiKeySource) = McpServer(
        name = "docs_mcp",
        url = "https://docs.example.test/mcp",
        type = McpServerType.SSE,
        apiKey = McpApiKeyConfig(source = source, authorizationType = McpAuthorizationType.BEARER),
    )

    /** The form as the dialog builds it on save, with the key field left as the user left it. */
    private fun saved(editing: McpServer?, key: String = "") =
        apiKeyConfigFrom(initialApiKeySource(editing), McpAuthorizationType.BEARER, key, customHeader = "")

    @Test
    fun `editing an admin-keyed server with a blank key keeps source admin and sends no key`() {
        assertThat(saved(server(McpApiKeySource.ADMIN))).isEqualTo(
            McpApiKeyConfig(source = McpApiKeySource.ADMIN, authorizationType = McpAuthorizationType.BEARER, key = null),
        )
    }

    @Test
    fun `editing an admin-keyed server with a new key sends it`() {
        assertThat(saved(server(McpApiKeySource.ADMIN), key = " sk-new ").key).isEqualTo("sk-new")
    }

    @Test
    fun `editing a user-keyed server keeps source user`() {
        assertThat(saved(server(McpApiKeySource.USER)).source).isEqualTo(McpApiKeySource.USER)
    }

    /** A per-user server never carries a key: each user supplies theirs, and the server strips one. */
    @Test
    fun `a per-user key is never sent`() {
        assertThat(saved(server(McpApiKeySource.USER), key = "typed-anyway").key).isNull()
    }

    @Test
    fun `a new server defaults to the admin supplying the key`() {
        assertThat(initialApiKeySource(null)).isEqualTo(McpApiKeySource.ADMIN)
        // An edit that switches auth to an API key has no stored source either.
        assertThat(initialApiKeySource(server(McpApiKeySource.USER).copy(apiKey = null))).isEqualTo(McpApiKeySource.ADMIN)
    }

    @Test
    fun `the custom header rides only with the custom authorization type`() {
        val custom = apiKeyConfigFrom(McpApiKeySource.ADMIN, McpAuthorizationType.CUSTOM, "k", " X-Api-Key ")
        val bearer = apiKeyConfigFrom(McpApiKeySource.ADMIN, McpAuthorizationType.BEARER, "k", "X-Api-Key")
        assertThat(custom.customHeader).isEqualTo("X-Api-Key")
        assertThat(bearer.customHeader).isNull()
    }
}
