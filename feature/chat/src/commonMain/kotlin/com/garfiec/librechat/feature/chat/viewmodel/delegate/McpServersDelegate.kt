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
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
     * Bumped by every MCP load, a sign-in's start and a sign-in's cancel, so a load that lands after
     * a newer one started (or after the chip went to connecting) is dropped rather than painting
     * older status. A newer [load] also cancels the older one's reads ([loadJob]); this is the
     * backstop for the reads a cancel's reload and a sign-in's start can't cancel.
     */
    private var loadGeneration = 0
    private var loadJob: Job? = null

    /** The server a chip's sign-in is reconnecting; a second tap while it runs is ignored. */
    private var signingIn: String? = null

    /** Whether [load] ran: only then does a return to the chat ([onForeground]) re-read the servers. */
    private var loaded = false

    /** Set by [onBackground]: the chat was behind something since its last [onForeground]. */
    private var wentAway = false

    /**
     * Tool discovery's last verdicts (`GET /api/mcp/tools`), kept across loads as the MCP servers
     * page keeps them: that route answers only after a full catalog load, so a load paints the
     * statuses with these and re-folds once its own discovery read lands.
     */
    private var discovery: Map<String, McpServerDiscovery> = emptyMap()

    /**
     * What the last load painted, so a discovery read that lands later re-folds onto it. The status
     * map is null when its read failed.
     */
    private var painted: Pair<List<McpServer>, Map<String, McpServerStatus>?>? = null

    /**
     * The last load listed servers whose discovery read waits for the tool controls
     * (`toolbarShown`): nothing in an agent or `hideBadgeRow` chat reads the verdicts, and that
     * route runs a full catalog load on the server, so it is asked only once the chat shows them
     * ([watchToolControls]), as web asks only while it observes tool authorization.
     */
    private var discoveryWanted = false

    /**
     * The discovery read still out. A newer read and a chip sign-in's start cancel it (on Main, so a
     * cancelled read never lands); a return's status-only reload ([onForeground]) asks for no
     * discovery and leaves it running, or nothing would ask for it again.
     */
    private var discoveryJob: Job? = null
    private var toolControlsWatch: Job? = null

    /**
     * A "Cancel sign-in" ([cancelSignIn]) still on its way to the server. A chip sign-in waits for it
     * before reconnecting: the server would otherwise replay the flow being cancelled into a new
     * sign-in, and the cancel landing after it would refuse that sign-in's callback.
     */
    private var pendingCancel: CompletableDeferred<Unit>? = null

    /**
     * A chip's reconnect or a cancel is out. [cancelSignIn] and a second [signIn] tap are ignored
     * until it ends; a [signIn] while only a cancel is out waits for that cancel before reconnecting.
     */
    private val signInBusy: Boolean get() = signingIn != null || pendingCancel != null

    /**
     * The servers with their status as the MCP servers page reads it: the connection-status map
     * with tool discovery's lapsed authorizations folded in ([applyDiscoveryAuthorizationState], as
     * the page does), mapped by [mcpDisplayStatus]. The servers show as soon as the list and the
     * status route answer. Discovery, which can take a live `tools/list` per cold server, is asked
     * after them, only for a non-empty list in a chat that shows the tool controls, and folds in
     * when it lands. A failed status read leaves the statuses unknown rather than failed; a failed
     * discovery read keeps the last verdicts.
     */
    fun load() = load(discover = true)

    /**
     * [discover] false (a return to the chat): the list and status reads only, with discovery asked
     * again only when the list of servers changed, since that route runs a full catalog load.
     */
    private fun load(discover: Boolean) {
        loaded = true
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = handle.scope.launch { reload(generation, discover) }
    }

    /** One load's list and status reads. A newer load or a chip tap since [generation] drops it. */
    private suspend fun reload(generation: Int, discover: Boolean = true) {
        if (generation != loadGeneration) return
        watchToolControls()
        coroutineScope {
            val status = async { mcpRepository.getConnectionStatus() }
            val serversResult = mcpRepository.listServers()
            if (serversResult !is Result.Success) {
                if (serversResult is Result.Error) {
                    Logger.d(serversResult.exception) { "Failed to load MCP servers: ${serversResult.message}" }
                }
                status.cancel()
                return@coroutineScope
            }
            val statusMap = (status.await() as? Result.Success)?.data
            if (generation != loadGeneration) return@coroutineScope
            val listChanged = painted?.first?.map { it.name } != serversResult.data.map { it.name }
            painted = serversResult.data to statusMap
            paint(serversResult.data, statusMap)
            if (discover || listChanged) {
                discoveryWanted = serversResult.data.isNotEmpty()
                if (discoveryWanted && handle.state.toolbarShown) fetchDiscovery()
            }
        }
    }

    /** Asks for discovery once the chat shows its tool controls, when the last load left it waiting. */
    private fun watchToolControls() {
        if (toolControlsWatch != null) return
        toolControlsWatch = handle.scope.launch {
            handle.stateFlow.map { it.toolbarShown }.distinctUntilChanged().collect { shown ->
                if (shown && discoveryWanted) fetchDiscovery()
            }
        }
    }

    private fun fetchDiscovery() {
        discoveryWanted = false
        discoveryJob?.cancel()
        discoveryJob = handle.scope.launch {
            val discovered = (mcpRepository.getTools() as? Result.Success)?.data?.servers ?: return@launch
            if (discovered == discovery) return@launch
            discovery = discovered
            painted?.let { (listed, statusMap) -> paint(listed, statusMap) }
        }
    }

    /**
     * Paints [listed] with [statusMap], or unknown statuses when that read failed (null). Writes
     * nothing when that is what the chat already shows.
     */
    private fun paint(listed: List<McpServer>, statusMap: Map<String, McpServerStatus>?) {
        val folded = applyDiscoveryAuthorizationState(statusMap.orEmpty(), discovery)
        val servers = listed.map { server ->
            val status = folded[server.name]
            server.toDisplayData(
                status = mcpDisplayStatus(server, status, isReinitializing = false),
                requiresOAuth = status?.requiresOAuth == true,
                signInBusy = signInBusy,
            )
        }
        if (servers == handle.state.mcpServers) return
        handle.update { selection = selection.copy(mcpServers = servers) }
    }

    /** Puts [signInBusy] on the servers on screen, as a reconnect or a cancel starts or ends. */
    private fun paintSignInBusy() {
        val busy = signInBusy
        if (handle.state.mcpServers.all { it.signInBusy == busy }) return
        handle.update {
            selection = selection.copy(mcpServers = selection.mcpServers.map { it.copy(signInBusy = busy) })
        }
    }

    /**
     * The chat is in front again: re-reads the servers and their statuses, since a chip's tap acts
     * on them and they may have changed meanwhile (a browser sign-in finished or abandoned, a sign-in
     * or sign-out on the MCP servers page, a tool call in another chat). Discovery is asked again
     * only when the list changed. Skipped while a chip's reconnect or a cancel is still out: that one
     * reloads when it ends, and a read now could paint over it with the status it is about to change.
     * Skipped too when [signInEnding]: a browser sign-in ends on this same return and its
     * [onSignInEnded] reloads. A resume with no pause before it (the chat's first) re-reads nothing:
     * the chat's own first load is out.
     */
    fun onForeground(signInEnding: Boolean) {
        val returned = wentAway
        wentAway = false
        if (!returned || !loaded || signInBusy || signInEnding) return
        load(discover = false)
    }

    /** The chat went behind another screen or app; its next [onForeground] re-reads. */
    fun onBackground() {
        wentAway = true
    }

    /**
     * A chip sign-in, as web's MCP palette runs one (`useMCPServerManager.initializeServer`): asks the
     * server to reconnect [serverName], which answers with the provider's sign-in link, and hands that
     * to [openSignIn] (the chat's `LocalMcpSignIn`). The chip reads connecting meanwhile. The tap on a
     * chip that needs sign-in, and "Start over" on one that reads connecting, both come here. Every
     * end reloads the statuses: here when no sign-in opens (already connected, deferred, refused,
     * failed, or [openSignIn] turned it down because another sign-in holds the screen), and through
     * [onSignInEnded] once an opened sign-in ends. [onFailed] is told when the reconnect could not
     * start a sign-in. The reconnect waits for a cancel still on its way ([pendingCancel]).
     *
     * Nothing here cancels the OAuth flow the reconnect leaves pending, whatever happens to the
     * sign-in: the user may finish it in the browser later, and a run elsewhere may be waiting on the
     * same flow. Only [cancelSignIn], the user's own "Cancel sign-in", cancels one.
     */
    fun signIn(
        serverName: String,
        /** Whether the sign-in opened; false while another one is binding or on screen. */
        openSignIn: (serverName: String, authorizationUrl: String) -> Boolean,
        onFailed: () -> Unit,
    ) {
        if (signingIn != null) return
        signingIn = serverName
        val cancelling = pendingCancel
        loadGeneration++
        discoveryJob?.cancel()
        // Connecting, and busy so no chip offers what this reconnect would ignore: its own menu
        // (Cancel sign-in, Start over) or another chip's sign-in.
        handle.update {
            selection = selection.copy(
                mcpServers = selection.mcpServers.map {
                    if (it.name == serverName) {
                        it.copy(status = McpDisplayStatus.CONNECTING, signInBusy = true)
                    } else {
                        it.copy(signInBusy = true)
                    }
                },
            )
        }
        handle.scope.launch {
            cancelling?.await()
            val result = mcpRepository.reinitialize(serverName)
            signingIn = null
            paintSignInBusy()
            val opened = when (result) {
                is Result.Success -> {
                    val response = result.data
                    val authorizationUrl = response.authorizationUrl
                    when {
                        authorizationUrl == null -> {
                            if (!response.success && response.connectionDeferred != true) onFailed()
                            false
                        }
                        openSignIn(serverName, authorizationUrl) -> true
                        else -> {
                            Logger.d { "Chip sign-in for $serverName not opened: another sign-in holds the screen" }
                            false
                        }
                    }
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to reconnect MCP server for sign-in: ${result.message}" }
                    onFailed()
                    false
                }
                is Result.Loading -> false
            }
            // An opened sign-in reloads through onSignInEnded once it ends.
            if (!opened) load()
        }
    }

    /**
     * An MCP sign-in ended, whichever way and wherever it started (a chip, a tool call): re-reads
     * the statuses. Nothing is cancelled: a closed or refused sign-in leaves its flow pending, which
     * the chip then shows as connecting, with "Cancel sign-in" and "Start over", as web does.
     */
    fun onSignInEnded() {
        load()
    }

    /**
     * The user's "Cancel sign-in" on a chip that reads connecting: cancels this user's pending OAuth
     * flow for [serverName] (`POST /api/mcp/oauth/cancel/:serverName`, web's `cancelOAuthFlow`), then
     * reloads the statuses, whether or not the cancel landed. [onFailed] is told when it didn't, since
     * the chip then reads connecting as before. Ignored while a chip's reconnect or another cancel is
     * out (the same guard as a second tap): that reconnect is what starts or replays the flow, and it
     * reloads when it ends.
     */
    fun cancelSignIn(serverName: String, onFailed: () -> Unit) {
        if (signInBusy) return
        val generation = ++loadGeneration
        val cancelled = CompletableDeferred<Unit>()
        pendingCancel = cancelled
        paintSignInBusy()
        handle.scope.launch {
            try {
                val result = mcpRepository.cancelOAuth(serverName)
                if (result is Result.Error) {
                    Logger.d(result.exception) { "Failed to cancel MCP OAuth for $serverName: ${result.message}" }
                    onFailed()
                }
            } finally {
                cancelled.complete(Unit)
                if (pendingCancel === cancelled) pendingCancel = null
                paintSignInBusy()
            }
            // Dropped when a chip sign-in started meanwhile: that one reloads when it ends.
            reload(generation)
        }
    }
}

internal fun McpServer.toDisplayData(
    status: McpDisplayStatus,
    requiresOAuth: Boolean,
    signInBusy: Boolean,
) = McpServerDisplayData(
    name = name,
    title = title,
    description = description,
    status = status,
    requiresOAuth = requiresOAuth,
    signInBusy = signInBusy,
)
