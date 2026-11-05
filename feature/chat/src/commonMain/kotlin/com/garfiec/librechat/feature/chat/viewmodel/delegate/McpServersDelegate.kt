package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.mcpDisplayStatus
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
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
     * The servers with their status as the MCP servers page reads it ([mcpDisplayStatus]), from the
     * connection-status map. A failed status read leaves every status unknown rather than failed.
     */
    fun load() {
        val generation = ++loadGeneration
        handle.scope.launch {
            when (val serversResult = mcpRepository.listServers()) {
                is Result.Success -> {
                    val statusResult = mcpRepository.getConnectionStatus()
                    val statusMap = (statusResult as? Result.Success)?.data ?: emptyMap()
                    if (generation != loadGeneration) return@launch
                    val servers = serversResult.data.map { server ->
                        server.toDisplayData(mcpDisplayStatus(server, statusMap[server.name], isReinitializing = false))
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

    /** An MCP sign-in ended, whichever way and wherever it started (a chip, a tool call): re-read the statuses. */
    fun onSignInEnded() = load()
}

internal fun McpServer.toDisplayData(status: McpDisplayStatus) = McpServerDisplayData(
    name = name,
    title = title,
    description = description,
    status = status,
)
