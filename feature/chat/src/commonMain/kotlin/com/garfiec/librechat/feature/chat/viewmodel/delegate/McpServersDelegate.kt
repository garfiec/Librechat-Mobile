package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.applyDiscoveryAuthorizationState
import com.garfiec.librechat.core.model.mcp.mcpDisplayStatus
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInEnd
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The chat's MCP servers and their status (`ModelSelectionState.mcpServers`), read for the settings
 * chips and the "+" sheet, and the chips' sign-in. Which servers are switched on stays with
 * [ModelSelectionDelegate.toggleMcpServer].
 */
class McpServersDelegate(
    private val handle: ModelSelectionHandle,
    private val mcpRepository: McpRepository,
) {
    /**
     * Bumped by every MCP load and by a sign-in's start, so a load that lands after a newer one
     * started (or after the chip went to connecting) is dropped rather than painting older status.
     */
    private var loadGeneration = 0

    /** The server a chip's sign-in is reconnecting; a second tap while it runs is ignored. */
    private var signingIn: String? = null

    /**
     * The server whose sign-in a chip opened, until that sign-in ends. Its end cancels the OAuth
     * flow the reconnect started when the user didn't finish it ([onSignInEnded]); a sign-in a tool
     * call opened is the run's, and is left alone.
     */
    private var chipSignIn: String? = null

    /**
     * The servers with their status as the MCP servers page reads it: the connection-status map
     * with tool discovery's lapsed authorizations folded in ([applyDiscoveryAuthorizationState], as
     * the page does), mapped by [mcpDisplayStatus]. A failed status read leaves the statuses unknown
     * rather than failed; a failed discovery read folds nothing.
     */
    fun load() {
        val generation = ++loadGeneration
        handle.scope.launch {
            when (val serversResult = mcpRepository.listServers()) {
                is Result.Success -> {
                    val statusMap = readStatuses()
                    if (generation != loadGeneration) return@launch
                    val servers = serversResult.data.map { server ->
                        val status = statusMap[server.name]
                        server.toDisplayData(
                            status = mcpDisplayStatus(server, status, isReinitializing = false),
                            requiresOAuth = status?.requiresOAuth == true,
                        )
                    }
                    handle.update { selection = selection.copy(mcpServers = servers) }
                }
                is Result.Error -> {
                    Logger.d(serversResult.exception) { "Failed to load MCP servers: ${serversResult.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private suspend fun readStatuses(): Map<String, McpServerStatus> = coroutineScope {
        val status = async { mcpRepository.getConnectionStatus() }
        val tools = async { mcpRepository.getTools() }
        val statusMap = (status.await() as? Result.Success)?.data ?: emptyMap()
        val discovery = (tools.await() as? Result.Success)?.data?.servers ?: emptyMap()
        applyDiscoveryAuthorizationState(statusMap, discovery)
    }

    /**
     * A tap on a settings chip whose server needs sign-in: asks the server to reconnect it, which
     * answers with the provider's sign-in link, and hands that to [openSignIn] (the chat's
     * `LocalMcpSignIn`). The chip reads connecting meanwhile. Every end reloads the statuses: here
     * when no sign-in opens (already connected, deferred, refused, failed), and through
     * [onSignInEnded] once an opened sign-in succeeds, is cancelled or fails. [onFailed] is told
     * when the reconnect could not start a sign-in.
     */
    fun signIn(
        serverName: String,
        openSignIn: (serverName: String, authorizationUrl: String) -> Unit,
        onFailed: () -> Unit,
    ) {
        if (signingIn != null) return
        signingIn = serverName
        loadGeneration++
        handle.update {
            selection = selection.copy(
                mcpServers = selection.mcpServers.map {
                    if (it.name == serverName) it.copy(status = McpDisplayStatus.CONNECTING) else it
                },
            )
        }
        handle.scope.launch {
            val result = mcpRepository.reinitialize(serverName)
            signingIn = null
            when (result) {
                is Result.Success -> {
                    val response = result.data
                    val authorizationUrl = response.oauthUrl?.takeIf { response.oauthRequired == true && it.isNotBlank() }
                    if (authorizationUrl != null) {
                        chipSignIn = serverName
                        openSignIn(serverName, authorizationUrl)
                        return@launch
                    }
                    if (!response.success && response.connectionDeferred != true) onFailed()
                    load()
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to reconnect MCP server for sign-in: ${result.message}" }
                    onFailed()
                    load()
                }
                is Result.Loading -> load()
            }
        }
    }

    /**
     * An MCP sign-in ended, whichever way and wherever it started (a chip, a tool call): re-read the
     * statuses. When a chip's ended unfinished (closed, refused, failed to bind, or a browser
     * sign-in the server still doesn't count as authorized), its reconnect left the server's OAuth
     * flow pending, which reads as connecting until it times out (10 minutes by default). So it is
     * cancelled first, as web's Cancel does, and the chip offers sign-in again at once.
     */
    fun onSignInEnded(end: McpSignInEnd?) {
        val server = chipSignIn?.takeIf { it == end?.serverName }
        if (server != null) chipSignIn = null
        if (server == null || end?.signedIn == true) {
            load()
            return
        }
        handle.scope.launch {
            val unfinished = end?.signedIn == false || !isConnected(server)
            if (unfinished) {
                val cancelled = mcpRepository.cancelOAuth(server)
                if (cancelled is Result.Error) {
                    Logger.d(cancelled.exception) { "Failed to cancel MCP OAuth for $server: ${cancelled.message}" }
                }
            }
            load()
        }
    }

    /** After a browser sign-in, whose result the app can't see: whether the server now reads connected. */
    private suspend fun isConnected(server: String): Boolean =
        (mcpRepository.getConnectionStatus() as? Result.Success)?.data?.get(server)?.isConnected == true
}

internal fun McpServer.toDisplayData(status: McpDisplayStatus, requiresOAuth: Boolean = false) = McpServerDisplayData(
    name = name,
    title = title,
    description = description,
    status = status,
    requiresOAuth = requiresOAuth,
)
