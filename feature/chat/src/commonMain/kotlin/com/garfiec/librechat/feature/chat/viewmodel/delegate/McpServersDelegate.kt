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
     * older status.
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
     * Whether [chipSignIn]'s reconnect started its OAuth flow. False when the server already read
     * that server connecting with a sign-in pending before the tap: it keeps one flow per user and
     * server, so the reconnect only replayed a flow somebody else is waiting on (a run in another
     * chat, a web tab, another device), and closing the dialog must leave it alone.
     */
    private var chipOwnsFlow = true

    /**
     * A chip's browser sign-in the app came back from while the server didn't read connected yet:
     * the user may still be finishing it in the browser, so every return to the chat re-reads the
     * statuses ([onForeground]) until this server reads anything but connecting.
     */
    private var browserSignIn: String? = null

    /** Whether [browserSignIn]'s flow was the chip's own ([chipOwnsFlow] when it went to the browser). */
    private var browserOwnsFlow = true

    /**
     * Tool discovery's last verdicts (`GET /api/mcp/tools`), kept across loads as the MCP servers
     * page keeps them: that route answers only after a full catalog load, so a load paints the
     * statuses with these and re-folds once its own discovery read lands.
     */
    private var discovery: Map<String, McpServerDiscovery> = emptyMap()

    /** What the last load painted, so a discovery read that lands later re-folds onto it. */
    private var painted: Pair<List<McpServer>, Map<String, McpServerStatus>>? = null

    /**
     * The last load listed servers whose discovery read waits for the tool controls
     * (`toolbarShown`): nothing in an agent or `hideBadgeRow` chat reads the verdicts, and that
     * route runs a full catalog load on the server, so it is asked only once the chat shows them
     * ([watchToolControls]), as web asks only while it observes tool authorization.
     */
    private var discoveryWanted = false
    private var discoveryJob: Job? = null
    private var toolControlsWatch: Job? = null

    /**
     * A chip sign-in's cancel ([cancelPendingFlow]) still on its way to the server. A chip tap
     * waits for it before reconnecting: the server would otherwise replay the flow being cancelled
     * into a new sign-in, and the cancel landing after it would refuse that sign-in's callback.
     */
    private var pendingCancel: CompletableDeferred<Unit>? = null

    /**
     * The servers with their status as the MCP servers page reads it: the connection-status map
     * with tool discovery's lapsed authorizations folded in ([applyDiscoveryAuthorizationState], as
     * the page does), mapped by [mcpDisplayStatus]. The servers show as soon as the list and the
     * status route answer. Discovery, which can take a live `tools/list` per cold server, is asked
     * after them, only for a non-empty list in a chat that shows the tool controls, and folds in
     * when it lands. A failed status read leaves the statuses unknown rather than failed; a failed
     * discovery read keeps the last verdicts.
     */
    fun load() {
        val generation = ++loadGeneration
        handle.scope.launch { reload(generation) }
    }

    /** One load's list and status reads. A newer load or a chip tap since [generation] drops it. */
    private suspend fun reload(generation: Int) {
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
            val statusMap = (status.await() as? Result.Success)?.data ?: emptyMap()
            if (generation != loadGeneration) return@coroutineScope
            painted = serversResult.data to statusMap
            paint(serversResult.data, statusMap)
            discoveryWanted = serversResult.data.isNotEmpty()
            if (discoveryWanted && handle.state.toolbarShown) fetchDiscovery()
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
        val generation = loadGeneration
        discoveryJob?.cancel()
        discoveryJob = handle.scope.launch {
            val discovered = (mcpRepository.getTools() as? Result.Success)?.data?.servers ?: return@launch
            if (generation != loadGeneration || discovered == discovery) return@launch
            discovery = discovered
            painted?.let { (listed, statusMap) -> paint(listed, statusMap) }
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
     * a sign-in. The reconnect waits for a cancel an earlier chip sign-in left on its way. A flow
     * the server already held pending at the tap is only replayed, and never cancelled from here.
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
        // A flow already pending that this chat didn't leave: not its own cancel still on the way
        // (the status is then stale) nor its own unfinished browser sign-in.
        val ownBrowserFlow = browserSignIn == serverName && browserOwnsFlow
        val replaysAnotherFlow = cancelling == null && !ownBrowserFlow &&
            handle.state.mcpServers.any {
                it.name == serverName && it.status == McpDisplayStatus.CONNECTING && it.requiresOAuth
            }
        loadGeneration++
        handle.update {
            selection = selection.copy(
                mcpServers = selection.mcpServers.map {
                    if (it.name == serverName) it.copy(status = McpDisplayStatus.CONNECTING) else it
                },
            )
        }
        handle.scope.launch {
            cancelling?.await()
            val result = mcpRepository.reinitialize(serverName)
            signingIn = null
            when (result) {
                is Result.Success -> {
                    val response = result.data
                    val authorizationUrl = response.oauthUrl?.takeIf { response.oauthRequired == true && it.isNotBlank() }
                    if (authorizationUrl != null) {
                        if (openSignIn(serverName, authorizationUrl)) {
                            chipSignIn = serverName
                            chipOwnsFlow = !replaysAnotherFlow
                            if (browserSignIn == serverName) browserSignIn = null
                            return@launch
                        }
                        // Nothing will end a sign-in that never opened, and the flow the reconnect
                        // left pending reads as connecting until it times out: it goes the way of a
                        // closed one.
                        Logger.d { "Chip sign-in for $serverName not opened: another sign-in holds the screen" }
                        if (replaysAnotherFlow) load() else cancelPendingFlow(serverName)
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
     * Cancel does, and the chip offers sign-in again at once ([cancelPendingFlow]).
     *
     * A browser sign-in (`signedIn == null`) is never cancelled: the app sees only that the user came
     * back, and they may come back mid-way (the app switcher, a split-screen pane) and then finish in
     * the browser, whose callback a cancelled flow would refuse. Its statuses are re-read on every
     * return until the server stops reading connecting ([browserSignIn]); a flow the user abandoned
     * is left to the server's timeout, or to the chip's next tap, which replays or reissues it.
     *
     * Nor is a flow the chip's reconnect only replayed ([chipOwnsFlow]): it was pending before the
     * tap, so it belongs to whoever started it, and cancelling it would fail their sign-in.
     */
    fun onSignInEnded(end: McpSignInEnd?) {
        val server = chipSignIn?.takeIf { it == end?.serverName }
        if (server != null) chipSignIn = null
        if (server == null || end?.signedIn != false || !chipOwnsFlow) {
            if (server != null && end?.signedIn == null) {
                browserSignIn = server
                browserOwnsFlow = chipOwnsFlow
            }
            load()
            return
        }
        cancelPendingFlow(server)
    }

    /**
     * Cancels the OAuth flow a chip's reconnect left pending on [server], then reloads. The server
     * keeps one flow per user and server, and a reconnect while a run's tool call waits on that
     * server's sign-in replays the run's own flow: cancelling it would fail the tool call. A run
     * elsewhere is caught at the tap ([chipOwnsFlow]); this chat's run is checked here, since its
     * flow may start after the tap. So while a reply runs, or a live call waits on that server,
     * the flow is left alone and only the statuses reload. The chip may then read connecting until
     * the run's sign-in ends; its tap replays the flow.
     */
    private fun cancelPendingFlow(server: String) {
        if (runMayHoldFlow(server)) {
            Logger.d { "Chip sign-in for $server not cancelled: a run may be waiting on its flow" }
            load()
            return
        }
        val generation = ++loadGeneration
        val cancelled = CompletableDeferred<Unit>()
        pendingCancel = cancelled
        handle.scope.launch {
            try {
                val result = mcpRepository.cancelOAuth(server)
                if (result is Result.Error) {
                    Logger.d(result.exception) { "Failed to cancel MCP OAuth for $server: ${result.message}" }
                }
            } finally {
                cancelled.complete(Unit)
                if (pendingCancel === cancelled) pendingCancel = null
            }
            // Dropped when a chip tap came meanwhile: that sign-in owns the chip now.
            reload(generation)
        }
    }

    private fun runMayHoldFlow(server: String): Boolean {
        val state = handle.state
        val comparison = state.comparisonState
        if (state.isStreaming || comparison.primaryIsStreaming || comparison.secondaryIsStreaming) return true
        return (state.activeToolCalls + comparison.primaryActiveToolCalls + comparison.secondaryActiveToolCalls)
            .any { it.authServerName == server && it.authUrl != null && !it.isComplete }
    }
}

internal fun McpServer.toDisplayData(status: McpDisplayStatus, requiresOAuth: Boolean = false) = McpServerDisplayData(
    name = name,
    title = title,
    description = description,
    status = status,
    requiresOAuth = requiresOAuth,
)
