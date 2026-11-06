package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerDiscovery
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
     * flow the reconnect started when the user closed or was refused it ([onSignInEnded]); a sign-in
     * a tool call opened is the run's, and is left alone.
     */
    private var chipSignIn: String? = null

    /**
     * A chip's browser sign-in the app came back from while the server didn't read connected yet:
     * the user may still be finishing it in the browser, so every return to the chat re-reads the
     * statuses ([onForeground]) until this server reads anything but connecting.
     */
    private var browserSignIn: String? = null

    /**
     * Tool discovery's last verdicts (`GET /api/mcp/tools`), kept across loads as the MCP servers
     * page keeps them: that route answers only after a full catalog load, so a load paints the
     * statuses with these and re-folds once its own discovery read lands.
     */
    private var discovery: Map<String, McpServerDiscovery> = emptyMap()

    /**
     * The servers with their status as the MCP servers page reads it: the connection-status map
     * with tool discovery's lapsed authorizations folded in ([applyDiscoveryAuthorizationState], as
     * the page does), mapped by [mcpDisplayStatus]. The servers show as soon as the list and the
     * status route answer; discovery, which can take a live `tools/list` per cold server, folds in
     * when it lands. A failed status read leaves the statuses unknown rather than failed; a failed
     * discovery read keeps the last verdicts.
     */
    fun load() {
        val generation = ++loadGeneration
        handle.scope.launch {
            coroutineScope {
                val tools = async { mcpRepository.getTools() }
                val status = async { mcpRepository.getConnectionStatus() }
                val serversResult = mcpRepository.listServers()
                if (serversResult !is Result.Success) {
                    if (serversResult is Result.Error) {
                        Logger.d(serversResult.exception) { "Failed to load MCP servers: ${serversResult.message}" }
                    }
                    tools.cancel()
                    status.cancel()
                    return@coroutineScope
                }
                val statusMap = (status.await() as? Result.Success)?.data ?: emptyMap()
                if (generation != loadGeneration) {
                    tools.cancel()
                    return@coroutineScope
                }
                paint(serversResult.data, statusMap)
                val discovered = (tools.await() as? Result.Success)?.data?.servers ?: return@coroutineScope
                if (generation != loadGeneration || discovered == discovery) return@coroutineScope
                discovery = discovered
                paint(serversResult.data, statusMap)
            }
        }
    }

    private fun paint(listed: List<McpServer>, statusMap: Map<String, McpServerStatus>) {
        val folded = applyDiscoveryAuthorizationState(statusMap, discovery)
        val servers = listed.map { server ->
            val status = folded[server.name]
            server.toDisplayData(
                status = mcpDisplayStatus(server, status, isReinitializing = false),
                requiresOAuth = status?.requiresOAuth == true,
            )
        }
        browserSignIn?.let { pending ->
            if (servers.none { it.name == pending && it.status == McpDisplayStatus.CONNECTING }) browserSignIn = null
        }
        handle.update { selection = selection.copy(mcpServers = servers) }
    }

    /** The chat is in front again: re-reads the statuses while a browser sign-in may still be finishing. */
    fun onForeground() {
        if (browserSignIn != null) load()
    }

    /**
     * A tap on a settings chip whose server needs sign-in: asks the server to reconnect it, which
     * answers with the provider's sign-in link, and hands that to [openSignIn] (the chat's
     * `LocalMcpSignIn`). The chip reads connecting meanwhile. Every end reloads the statuses: here
     * when no sign-in opens (already connected, deferred, refused, failed, or [openSignIn] turned
     * it down because another sign-in holds the screen), and through [onSignInEnded] once an opened
     * sign-in succeeds, is cancelled or fails. [onFailed] is told when the reconnect could not start
     * a sign-in.
     */
    fun signIn(
        serverName: String,
        /** Whether the sign-in opened; false while another one is binding or on screen. */
        openSignIn: (serverName: String, authorizationUrl: String) -> Boolean,
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
                        if (openSignIn(serverName, authorizationUrl)) {
                            chipSignIn = serverName
                            if (browserSignIn == serverName) browserSignIn = null
                            return@launch
                        }
                        // Nothing will end a sign-in that never opened. The flow the reconnect
                        // left pending is replayed or reissued by the chip's next tap.
                        Logger.d { "Chip sign-in for $serverName not opened: another sign-in holds the screen" }
                        load()
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
     * statuses. When a chip's in-app sign-in was closed or refused, or never opened or bound
     * (`signedIn == false`), its reconnect left the server's OAuth flow pending, which reads as
     * connecting until it times out (10 minutes by default). So it is cancelled first, as web's
     * Cancel does, and the chip offers sign-in again at once.
     *
     * A browser sign-in (`signedIn == null`) is never cancelled: the app sees only that the user came
     * back, and they may come back mid-way (the app switcher, a split-screen pane) and then finish in
     * the browser, whose callback a cancelled flow would refuse. Its statuses are re-read on every
     * return until the server stops reading connecting ([browserSignIn]); a flow the user abandoned
     * is left to the server's timeout, or to the chip's next tap, which replays or reissues it.
     */
    fun onSignInEnded(end: McpSignInEnd?) {
        val server = chipSignIn?.takeIf { it == end?.serverName }
        if (server != null) chipSignIn = null
        if (server == null || end?.signedIn != false) {
            if (server != null && end?.signedIn == null) browserSignIn = server
            load()
            return
        }
        handle.scope.launch {
            val cancelled = mcpRepository.cancelOAuth(server)
            if (cancelled is Result.Error) {
                Logger.d(cancelled.exception) { "Failed to cancel MCP OAuth for $server: ${cancelled.message}" }
            }
            load()
        }
    }
}

internal fun McpServer.toDisplayData(status: McpDisplayStatus, requiresOAuth: Boolean = false) = McpServerDisplayData(
    name = name,
    title = title,
    description = description,
    status = status,
    requiresOAuth = requiresOAuth,
)
