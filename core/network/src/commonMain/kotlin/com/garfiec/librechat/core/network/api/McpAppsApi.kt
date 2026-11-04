package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The MCP App routes (v0.8.9-rc1, `api/server/routes/mcp.js`). Bodies are bare JSON, not
 * `arg`-wrapped, and every call names the App's server and the opaque binding its attachment
 * carries. Results are the raw MCP SDK results, returned as-is because they are handed to the App
 * verbatim. The App's WebView never sees a token: these run natively on the App's behalf.
 *
 * Every route answers 403 `{error:"MCP Apps are disabled"}` when the server's policy is off, and
 * 400 for a binding the server no longer honours; `McpAppsRepository` classifies the failures.
 */
class McpAppsApi(private val client: HttpClient) {

    /** `POST /api/mcp/app/validate` → `{valid: true}`. Checked before running stored inline HTML. */
    suspend fun validate(serverName: String, serverBinding: String): JsonObject =
        post(listOf("app", "validate"), binding(serverName, serverBinding))

    /** `POST /api/mcp/resources/read` → a `ReadResourceResult`. */
    suspend fun readResource(serverName: String, serverBinding: String, uri: String): JsonObject =
        post(listOf("resources", "read"), binding(serverName, serverBinding) { put("uri", uri) })

    /** `POST /api/mcp/resources/list` → a `ListResourcesResult`. */
    suspend fun listResources(serverName: String, serverBinding: String, cursor: String?): JsonObject =
        post(listOf("resources", "list"), binding(serverName, serverBinding) { cursor?.let { put("cursor", it) } })

    /** `POST /api/mcp/resources/templates/list` → a `ListResourceTemplatesResult`. */
    suspend fun listResourceTemplates(serverName: String, serverBinding: String, cursor: String?): JsonObject =
        post(
            listOf("resources", "templates", "list"),
            binding(serverName, serverBinding) { cursor?.let { put("cursor", it) } },
        )

    /**
     * `POST /api/mcp/app-tool-call` → a `CallToolResult`. A tool whose `_meta.ui.visibility`
     * excludes `app` is refused with a 400.
     */
    suspend fun callTool(
        serverName: String,
        serverBinding: String,
        toolName: String,
        arguments: JsonObject,
    ): JsonObject = post(
        listOf("app-tool-call"),
        binding(serverName, serverBinding) {
            put("toolName", toolName)
            put("arguments", arguments)
        },
    )

    private suspend fun post(segments: List<String>, body: JsonElement): JsonObject =
        client.post {
            url { appendPathSegments(listOf("api", "mcp") + segments) }
            setBody(body)
        }.body()

    private fun binding(
        serverName: String,
        serverBinding: String,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("serverName", serverName)
        put("serverBinding", serverBinding)
        extra()
    }
}
