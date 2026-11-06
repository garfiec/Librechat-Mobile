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
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInEnd
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    /**
     * Bumped by every discovery read and a chip sign-in's start, so a discovery read lands unless a
     * newer one replaced it or a sign-in since may have changed what it found. Apart from
     * [loadGeneration]: a return's status-only reload ([onForeground]) asks for no discovery, so it
     * must not drop the one still out, or nothing would ask for it again.
     */
    private var discoveryGeneration = 0

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
     * Servers whose OAuth flow a chip's own sign-in left pending because the cancel failed, or the
     * status read after a run let go of the flow did ([cancelOnceRunLetsGo]). The server then reads
     * connecting with a sign-in pending, as it would for somebody else's flow, but the chip's next
     * tap only replays its own, so closing that sign-in still cancels it. A server leaves the set
     * once a status read that loaded shows it anything but connecting; a failed read says nothing
     * about the flow and keeps it.
     */
    private val leftPendingFlows = mutableSetOf<String>()

    /**
     * Servers whose chip sign-in closed while a run might wait on their flow ([runMayHoldFlow]), each
     * with the job that cancels the flow once that run lets go of it, if it is still pending then.
     * Held only while the flow can still be told for the chip's own: a loaded status that reads the
     * server anything but connecting drops it, since that flow ended and a later one may be anybody's.
     */
    private val heldByRun = mutableMapOf<String, Job>()

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
    fun load() = load(discover = true)

    /**
     * [discover] false (a return to the chat): the list and status reads only, with discovery asked
     * again only when the list of servers changed, since that route runs a full catalog load.
     */
    private fun load(discover: Boolean) {
        loaded = true
        val generation = ++loadGeneration
        handle.scope.launch { reload(generation, discover) }
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
        val generation = ++discoveryGeneration
        discoveryJob?.cancel()
        discoveryJob = handle.scope.launch {
            val discovered = (mcpRepository.getTools() as? Result.Success)?.data?.servers ?: return@launch
            if (generation != discoveryGeneration || discovered == discovery) return@launch
            discovery = discovered
            painted?.let { (listed, statusMap) -> paint(listed, statusMap) }
        }
    }

    /**
     * Paints [listed] with [statusMap], or unknown statuses when that read failed (null). Only a map
     * that loaded tells whether a flow the chat follows ended, so only then are those let go.
     */
    private fun paint(listed: List<McpServer>, statusMap: Map<String, McpServerStatus>?) {
        val folded = applyDiscoveryAuthorizationState(statusMap.orEmpty(), discovery)
        val servers = listed.map { server ->
            val status = folded[server.name]
            server.toDisplayData(
                status = mcpDisplayStatus(server, status, isReinitializing = false),
                requiresOAuth = status?.requiresOAuth == true,
            )
        }
        if (statusMap != null) {
            fun stillConnecting(name: String) = servers.any { it.name == name && it.status == McpDisplayStatus.CONNECTING }
            browserSignIn?.let { pending -> if (!stillConnecting(pending)) browserSignIn = null }
            leftPendingFlows.retainAll(::stillConnecting)
            heldByRun.keys.filterNot(::stillConnecting).forEach { server -> heldByRun.remove(server)?.cancel() }
        }
        handle.update { selection = selection.copy(mcpServers = servers) }
    }

    /**
     * The chat is in front again: re-reads the servers and their statuses, since a chip's tap acts
     * on them and they may have changed elsewhere meanwhile (a sign-in or sign-out on the MCP servers
     * page, a tool call in another chat). Discovery is asked again only while a chip's browser
     * sign-in may still be finishing ([browserSignIn]), or when the list changed. Skipped while a
     * chip sign-in, or its cancel, is still out: that one reloads when it ends, and a read now could
     * paint over it with the status it is about to change. A resume with no pause before it (the
     * chat's first) re-reads nothing: the chat's own first load is out.
     */
    fun onForeground() {
        val returned = wentAway
        wentAway = false
        if (!returned && browserSignIn == null) return
        if (!loaded || signingIn != null || chipSignIn != null || pendingCancel != null) return
        load(discover = browserSignIn != null)
    }

    /** The chat went behind another screen or app; its next [onForeground] re-reads. */
    fun onBackground() {
        wentAway = true
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
        // (the status is then stale), its own unfinished browser sign-in, nor a flow its own sign-in
        // left uncancelled (a run still holds it, or the cancel failed). A stored-token reconnect the
        // server runs on its own also reads connecting with a sign-in pending (upstream
        // getServerConnectionStatus, isReconnecting), and the status can't tell it from another
        // client's flow: a flow the chip's reconnect then starts is taken for that one's and left to
        // the server's timeout.
        val ownLeftoverFlow = (browserSignIn == serverName && browserOwnsFlow) ||
            serverName in leftPendingFlows || serverName in heldByRun
        val replaysAnotherFlow = cancelling == null && !ownLeftoverFlow &&
            handle.state.mcpServers.any {
                it.name == serverName && it.status == McpDisplayStatus.CONNECTING && it.requiresOAuth
            }
        loadGeneration++
        discoveryGeneration++
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
     * the flow is left alone and only the statuses reload, and the cancel waits for the run to let
     * go of it ([cancelOnceRunLetsGo]). The chip may read connecting meanwhile; its tap replays the
     * flow.
     */
    private fun cancelPendingFlow(server: String) {
        if (runMayHoldFlow(server)) {
            Logger.d { "Chip sign-in for $server not cancelled yet: a run may be waiting on its flow" }
            cancelOnceRunLetsGo(server)
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
                    leftPendingFlows += server
                } else {
                    leftPendingFlows -= server
                }
            } finally {
                cancelled.complete(Unit)
                if (pendingCancel === cancelled) pendingCancel = null
            }
            // Dropped when a chip tap came meanwhile: that sign-in owns the chip now.
            reload(generation)
        }
    }

    /**
     * Once no run in this chat may wait on [server]'s flow, cancels it if the server still reads it
     * pending. Not at the chip's next tap: by then a run on the web or another device may have
     * picked the same flow up, and the chat couldn't tell. A chip sign-in on [server] started since
     * decides on its own end. When the status read fails the flow is left for the next close
     * ([leftPendingFlows]).
     */
    private fun cancelOnceRunLetsGo(server: String) {
        if (server in heldByRun) return
        // Started once it is in the map, so the identity checks below see it there.
        val watch = handle.scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                handle.stateFlow.first { !runMayHoldFlow(server, it) }
                if (signingIn == server || chipSignIn == server) return@launch
                val statusMap = (mcpRepository.getConnectionStatus() as? Result.Success)?.data
                if (signingIn == server || chipSignIn == server || heldByRun[server] !== self) return@launch
                heldByRun.remove(server)
                val status = statusMap?.get(server)
                val stillPending = status?.requiresOAuth == true &&
                    mcpDisplayStatus(McpServer(name = server), status, isReinitializing = false) == McpDisplayStatus.CONNECTING
                when {
                    statusMap == null -> {
                        leftPendingFlows += server
                        load(discover = false)
                    }
                    stillPending -> cancelPendingFlow(server)
                    else -> load(discover = false)
                }
            } finally {
                if (heldByRun[server] === self) heldByRun.remove(server)
            }
        }
        heldByRun[server] = watch
        watch.start()
    }

    private fun runMayHoldFlow(server: String, state: ChatUiState = handle.state): Boolean {
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
