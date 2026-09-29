package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

/**
 * The MCP server write body, serialized through a real client with the app's JSON configuration.
 *
 * The body has to survive Ktor's content negotiation, not just look right as a Kotlin value: built
 * as a `Map<String, Any>`, a server with API-key or OAuth auth nests a map beside strings, the
 * serializer Ktor guesses for that map throws before any request is sent, and the save does nothing.
 * A server with no auth has only string values and serializes fine, which is how the failure hid.
 */
class McpApiServerBodyTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(
        response: String = """{"serverName":"docs_mcp","url":"https://docs.example.test/mcp","title":"Docs"}""",
    ): McpApi {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = response,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            // Mirrors production's defaultRequest, which is what makes the body serialize as JSON.
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return McpApi(client, librechatJson)
    }

    private fun sentBody(): JsonElement = Json.parseToJsonElement((requests.last().body as TextContent).text)

    private val apiKey = McpApiKeyConfig(
        source = McpApiKeySource.USER,
        authorizationType = McpAuthorizationType.CUSTOM,
        key = "sk-secret",
        customHeader = "X-Api-Key",
    )

    private val oauth = McpOAuthConfig(
        authorizationUrl = "https://auth.example.test/authorize",
        tokenUrl = "https://auth.example.test/token",
        clientId = "client-1",
        clientSecret = "shh",
        scope = "read",
    )

    private val apiKeyBody = Json.parseToJsonElement(
        """{"config":{"url":"https://docs.example.test/mcp","type":"sse","title":"Docs","description":"Team docs",
           "apiKey":{"source":"user","authorization_type":"custom","key":"sk-secret","custom_header":"X-Api-Key"}}}""",
    )

    private val oauthBody = Json.parseToJsonElement(
        """{"config":{"url":"https://docs.example.test/mcp","type":"sse","title":"Docs",
           "oauth":{"authorization_url":"https://auth.example.test/authorize","token_url":"https://auth.example.test/token",
           "client_id":"client-1","client_secret":"shh","scope":"read"}}}""",
    )

    @Test
    fun `creating a server with an API key sends the nested apiKey object`() = runTest {
        api().createServer(
            name = "Docs",
            description = "Team docs",
            url = "https://docs.example.test/mcp",
            type = McpServerType.SSE,
            apiKey = apiKey,
        )

        assertThat(requests.single().method).isEqualTo(HttpMethod.Post)
        assertThat(sentBody()).isEqualTo(apiKeyBody)
    }

    @Test
    fun `creating a server with OAuth sends the nested oauth object`() = runTest {
        api().createServer(name = "Docs", url = "https://docs.example.test/mcp", type = McpServerType.SSE, oauth = oauth)

        assertThat(sentBody()).isEqualTo(oauthBody)
    }

    @Test
    fun `editing a server with an API key patches the same body`() = runTest {
        api().updateServer(
            serverName = "docs_mcp",
            name = "Docs",
            description = "Team docs",
            url = "https://docs.example.test/mcp",
            type = McpServerType.SSE,
            apiKey = apiKey,
        )

        assertThat(requests.single().method).isEqualTo(HttpMethod.Patch)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/mcp/servers/docs_mcp")
        assertThat(sentBody()).isEqualTo(apiKeyBody)
    }

    @Test
    fun `editing a server with OAuth patches the same body`() = runTest {
        api().updateServer(
            serverName = "docs_mcp",
            name = "Docs",
            url = "https://docs.example.test/mcp",
            type = McpServerType.SSE,
            oauth = oauth,
        )

        assertThat(sentBody()).isEqualTo(oauthBody)
    }

    /** A server with no auth: blank optionals are omitted. */
    @Test
    fun `a server with no auth sends only its connection fields`() = runTest {
        api().createServer(name = "Docs", description = " ", url = "https://docs.example.test/mcp", type = McpServerType.STREAMABLE_HTTP)

        assertThat(sentBody()).isEqualTo(
            Json.parseToJsonElement(
                """{"config":{"url":"https://docs.example.test/mcp","type":"streamable-http","title":"Docs"}}""",
            ),
        )
    }

    /**
     * An admin-keyed edit with the key left blank sends `source: "admin"` and no `key` — the shape
     * that asks the server to keep the stored key, and from v0.8.8-rc4 lets it refuse with
     * `MCP_API_KEY_REENTRY_REQUIRED` when the connection changed. Sent as `user`, the same edit is a
     * 200 that silently discards the shared key.
     */
    @Test
    fun `an admin-keyed edit with a blank key sends source admin and no key`() = runTest {
        api().updateServer(
            serverName = "docs_mcp",
            name = "Docs",
            url = "https://moved.example.test/mcp",
            type = McpServerType.SSE,
            apiKey = McpApiKeyConfig(source = McpApiKeySource.ADMIN, authorizationType = McpAuthorizationType.BEARER),
        )

        assertThat(sentBody()).isEqualTo(
            Json.parseToJsonElement(
                """{"config":{"url":"https://moved.example.test/mcp","type":"sse","title":"Docs",
                   "apiKey":{"source":"admin","authorization_type":"bearer"}}}""",
            ),
        )
    }

    /**
     * The stored source has to survive the read, because the edit dialog resends it. Reads never
     * return the key, only who supplies it; upstream's form treats anything but "user" as admin.
     */
    @Test
    fun `the listed server keeps the stored key source`() = runTest {
        val servers = api(
            """{
              "shared":{"type":"sse","url":"https://a.test/mcp","apiKey":{"source":"admin","authorization_type":"bearer"}},
              "personal":{"type":"sse","url":"https://b.test/mcp","apiKey":{"source":"user","authorization_type":"bearer"}},
              "unlabelled":{"type":"sse","url":"https://c.test/mcp","apiKey":{"authorization_type":"bearer"}}
            }""",
        ).listServers().associateBy { it.name }

        assertThat(servers.getValue("shared").apiKey?.source).isEqualTo(McpApiKeySource.ADMIN)
        assertThat(servers.getValue("personal").apiKey?.source).isEqualTo(McpApiKeySource.USER)
        assertThat(servers.getValue("unlabelled").apiKey?.source).isEqualTo(McpApiKeySource.ADMIN)
    }

    /**
     * The update route replaces the stored config and counts `oauth.token_exchange_method` among the
     * fields the stored secret is bound to, so an edit that drops it is refused with
     * `MCP_OAUTH_SECRET_REENTRY_REQUIRED` although no endpoint changed. It has to survive the read
     * and go back out on the write.
     */
    @Test
    fun `an OAuth server's token exchange method survives the read and the write`() = runTest {
        val listed = api(
            """{"docs_mcp":{"type":"sse","url":"https://docs.example.test/mcp",
               "oauth":{"client_id":"client-1","token_exchange_method":"basic_auth_header"}}}""",
        ).listServers().single()
        assertThat(listed.oauth?.tokenExchangeMethod).isEqualTo("basic_auth_header")

        api().updateServer(
            serverName = "docs_mcp",
            name = "Docs",
            url = "https://docs.example.test/mcp",
            type = McpServerType.SSE,
            oauth = McpOAuthConfig(clientId = "client-1", tokenExchangeMethod = listed.oauth?.tokenExchangeMethod),
        )

        assertThat(sentBody()).isEqualTo(
            Json.parseToJsonElement(
                """{"config":{"url":"https://docs.example.test/mcp","type":"sse","title":"Docs",
                   "oauth":{"client_id":"client-1","token_exchange_method":"basic_auth_header"}}}""",
            ),
        )
    }

    /**
     * The update route replaces the stored config, so an edit resends what this app never edits.
     * Read a server the web configured with an icon, OBO scopes and an OAuth token exchange method,
     * then edit its title: all three must go back exactly as read, or the edit deletes them — and
     * on an OBO server a caller without CONFIGURE_OBO is refused with 403 for dropping `obo`.
     */
    @Test
    fun `an edit round-trips the icon, the OBO scopes and the token exchange method`() = runTest {
        val listed = api(
            """{"docs_mcp":{"type":"streamable-http","url":"https://docs.example.test/mcp","title":"Docs",
               "iconPath":"https://docs.example.test/icon.png","obo":{"scopes":"api://docs/Mcp.Tools"},
               "oauth":{"client_id":"c","token_exchange_method":"basic_auth_header"}}}""",
        ).listServers().single()

        api().updateServer(
            serverName = listed.name,
            name = "Docs (renamed)",
            url = listed.url,
            type = listed.type,
            oauth = listed.oauth,
            iconPath = listed.iconPath,
            obo = listed.obo,
        )

        assertThat(sentBody()).isEqualTo(
            Json.parseToJsonElement(
                """{"config":{"url":"https://docs.example.test/mcp","type":"streamable-http","title":"Docs (renamed)",
                   "iconPath":"https://docs.example.test/icon.png","obo":{"scopes":"api://docs/Mcp.Tools"},
                   "oauth":{"client_id":"c","token_exchange_method":"basic_auth_header"}}}""",
            ),
        )
    }
}
