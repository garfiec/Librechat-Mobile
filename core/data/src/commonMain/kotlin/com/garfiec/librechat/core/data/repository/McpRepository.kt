package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpOboConfig
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Repository for MCP server management. Uses serverName as unique identifier for operations. */
interface McpRepository {
    suspend fun listServers(): Result<List<McpServer>>
    suspend fun createServer(
        name: String,
        description: String? = null,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig? = null,
        oauth: McpOAuthConfig? = null,
        iconPath: String? = null,
        obo: McpOboConfig? = null,
    ): Result<McpServer>

    /**
     * Edits an existing server. Distinct from [createServer] on the wire (`PATCH` vs `POST`) and
     * server-side: only the update route re-validates the stored OAuth client secret against the
     * endpoints being saved, so an edit sent as a create both skips that check and collides with
     * the name it is meant to be editing.
     */
    suspend fun updateServer(
        serverName: String,
        name: String,
        description: String? = null,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig? = null,
        oauth: McpOAuthConfig? = null,
        iconPath: String? = null,
        obo: McpOboConfig? = null,
    ): Result<McpServer>
    suspend fun deleteServer(serverName: String): Result<Unit>
    suspend fun reinitialize(serverName: String): Result<McpReinitializeResponse>

    /**
     * Has the server set the OAuth binding cookies for [serverName] and returns them as `Set-Cookie`
     * values (see `McpApi.bindOAuth`), for an in-app sign-in to seed into its WebView.
     */
    suspend fun bindOAuth(serverName: String): Result<List<String>>

    /** Cancels this user's pending OAuth flow for [serverName] (see `McpApi.cancelOAuth`). */
    suspend fun cancelOAuth(serverName: String): Result<Unit>

    suspend fun getTools(): Result<McpToolCatalog>
    suspend fun getConnectionStatus(): Result<Map<String, McpServerStatus>>

    /** Which of [serverName]'s user variables this user has saved, by key (see `McpApi.getUserVarFlags`). */
    suspend fun getUserVarFlags(serverName: String): Result<Map<String, Boolean>>

    /** Saves the non-blank [values]; a blank one would erase the saved value (see `McpApi.saveUserVars`). */
    suspend fun saveUserVars(serverName: String, values: Map<String, String>): Result<Unit>

    /** Deletes this user's variables for [serverName], and its OAuth tokens with them. */
    suspend fun removeUserVars(serverName: String): Result<Unit>
}

/**
 * The servers among [servers] still owed one of their [McpServer.customUserVars], read in parallel.
 * A server whose read fails is left out, so it shows its plain connection status.
 */
suspend fun McpRepository.serversMissingUserVars(servers: List<McpServer>): Set<String> = coroutineScope {
    servers.filter { it.customUserVars.isNotEmpty() }
        .map { server ->
            async {
                val flags = (getUserVarFlags(server.name) as? Result.Success)?.data ?: return@async null
                server.name.takeIf { server.customUserVars.any { flags[it.key] != true } }
            }
        }
        .awaitAll()
        .filterNotNull()
        .toSet()
}
