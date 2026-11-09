package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.serversMissingUserVars
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

/** The chat's MCP servers, their status and the chips' sign-in; which are on is [ModelSelectionDelegate.toggleMcpServer]'s. */
class McpServersDelegate(
    private val handle: ModelSelectionHandle,
    private val mcpRepository: McpRepository,
    /** [ModelSelectionDelegate.toggleMcpServer]: how a sheet sign-in turns its server on. */
    private val toggleMcpServer: (String) -> Unit,
) {
    /**
     * Bumped by every load, sign-in start and cancel; an older generation paints nothing. The
     * backstop for reads [loadJob]'s cancel doesn't reach.
     */
    private var loadGeneration = 0
    private var loadJob: Job? = null

    private var signingIn: String? = null

    /**
     * A sheet sign-in's server, turned on once a reload reads it connected (web: sign in, then
     * select). Kept while it reads connecting; dropped by any other end.
     */
    private var selectOnConnect: String? = null

    private var loaded = false

    private var wentAway = false

    /**
     * Kept across loads: `GET /api/mcp/tools` answers only after a full catalog load, so a load
     * paints with these verdicts and re-folds when its own read lands.
     */
    private var discovery: Map<String, McpServerDiscovery> = emptyMap()

    /** The last load's list and status map (null: that read failed), for a later discovery read to re-fold. */
    private var painted: Pair<List<McpServer>, Map<String, McpServerStatus>?>? = null

    /** The last load's servers still owed user variables ([serversMissingUserVars]). */
    private var missingUserVars: Set<String> = emptySet()

    /** Discovery waits for `toolbarShown`: the route runs a full catalog load and no chat without tool controls reads it. */
    private var discoveryWanted = false

    /**
     * Cancelled only by a newer read or a chip sign-in (on Main, so it never lands); a return's
     * status-only reload leaves it running, or nothing would ask for it again.
     */
    private var discoveryJob: Job? = null
    private var toolControlsWatch: Job? = null

    /**
     * A sign-in waits for this cancel before reconnecting: the server would replay the flow being
     * cancelled, and the late cancel would refuse the new sign-in's callback.
     */
    private var pendingCancel: CompletableDeferred<Unit>? = null

    private val signInBusy: Boolean get() = signingIn != null || pendingCancel != null

    /**
     * Paints the servers with their status as the MCP servers page reads it; discovery folds in when
     * it lands. A failed status read paints unknown, not failed; a failed discovery read keeps the last verdicts.
     */
    fun load() = load(discover = true)

    private fun load(discover: Boolean) {
        loaded = true
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = handle.scope.launch { reload(generation, discover) }
    }

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
            val missing = async { mcpRepository.serversMissingUserVars(serversResult.data) }
            val statusMap = (status.await() as? Result.Success)?.data
            val missingNow = missing.await()
            if (generation != loadGeneration) return@coroutineScope
            val listChanged = painted?.first?.map { it.name } != serversResult.data.map { it.name }
            painted = serversResult.data to statusMap
            missingUserVars = missingNow
            paint(serversResult.data, statusMap)
            if (discover || listChanged) {
                discoveryWanted = serversResult.data.isNotEmpty()
                if (discoveryWanted && handle.state.toolbarShown) fetchDiscovery()
            }
        }
    }

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

    private fun paint(listed: List<McpServer>, statusMap: Map<String, McpServerStatus>?) {
        val folded = applyDiscoveryAuthorizationState(statusMap.orEmpty(), discovery)
        val servers = listed.map { server ->
            val status = folded[server.name]
            server.toDisplayData(
                status = mcpDisplayStatus(
                    server,
                    status,
                    isReinitializing = false,
                    userVarsMissing = server.name in missingUserVars,
                ),
                requiresOAuth = status?.requiresOAuth == true,
                signInBusy = signInBusy,
            )
        }
        if (servers != handle.state.mcpServers) handle.update { selection = selection.copy(mcpServers = servers) }
        if (statusMap != null && signingIn == null) selectIfConnected(servers)
    }

    private fun selectIfConnected(servers: List<McpServerDisplayData>) {
        val name = selectOnConnect ?: return
        when (servers.firstOrNull { it.name == name }?.status) {
            McpDisplayStatus.CONNECTING -> return
            McpDisplayStatus.CONNECTED -> {
                selectOnConnect = null
                if (name !in handle.state.effectiveMcpServerNames) toggleMcpServer(name)
            }
            else -> selectOnConnect = null
        }
    }

    private fun paintSignInBusy() {
        val busy = signInBusy
        if (handle.state.mcpServers.all { it.signInBusy == busy }) return
        handle.update {
            selection = selection.copy(mcpServers = selection.mcpServers.map { it.copy(signInBusy = busy) })
        }
    }

    /**
     * Re-reads on a return to the chat. Skipped while a reconnect or cancel is out (it reloads when it
     * ends; a read now could paint over it) and when [signInEnding] ([onSignInEnded] reloads).
     */
    fun onForeground(signInEnding: Boolean) {
        val returned = wentAway
        wentAway = false
        if (!returned || !loaded || signInBusy || signInEnding) return
        load(discover = false)
    }

    fun onBackground() {
        wentAway = true
    }

    /**
     * Reconnects [serverName] for its sign-in link (web's `useMCPServerManager.initializeServer`) and
     * hands it to [openSignIn]. Every end reloads: here when no sign-in opens, else [onSignInEnded].
     * Never cancels the OAuth flow it leaves pending: the user may finish it later and a run elsewhere
     * may wait on it. Only [cancelSignIn] cancels one.
     */
    fun signIn(
        serverName: String,
        /** Whether the sign-in opened; false while another one is binding or on screen. */
        openSignIn: (serverName: String, authorizationUrl: String) -> Boolean,
        onFailed: () -> Unit,
        /** The "+" sheet's sign-in: turn the server on once it connects. A chip's never does. */
        selectWhenConnected: Boolean = false,
    ) {
        if (signingIn != null) return
        signingIn = serverName
        // A chip's sign-in leaves another server's pending sheet select alone.
        if (selectWhenConnected) selectOnConnect = serverName
        val cancelling = pendingCancel
        loadGeneration++
        discoveryJob?.cancel()
        // Busy on every chip: this reconnect ignores their sign-in and menu taps.
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
                            if (!response.success && response.connectionDeferred != true) failed(serverName, onFailed)
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
                    failed(serverName, onFailed)
                    false
                }
                is Result.Loading -> false
            }
            // An opened sign-in reloads through onSignInEnded once it ends.
            if (!opened) load()
        }
    }

    private fun failed(serverName: String, onFailed: () -> Unit) {
        if (selectOnConnect == serverName) selectOnConnect = null
        onFailed()
    }

    /** Which of [serverName]'s variables are saved, for the variables sheet; null when the read failed. */
    suspend fun userVarFlags(serverName: String): Map<String, Boolean>? =
        (mcpRepository.getUserVarFlags(serverName) as? Result.Success)?.data

    /**
     * Saves the variables sheet's [values] (none: the user only connects). False when the server refused;
     * the caller then reconnects through [signIn], which selects the server once it connects.
     */
    suspend fun saveUserVars(serverName: String, values: Map<String, String>): Boolean {
        if (values.isEmpty()) return true
        val result = mcpRepository.saveUserVars(serverName, values)
        if (result is Result.Error) Logger.d(result.exception) { "Failed to save MCP variables: ${result.message}" }
        return result is Result.Success
    }

    /** Deletes [serverName]'s variables, turns the server off in this chat (as web does) and reloads. */
    suspend fun removeUserVars(serverName: String): Boolean {
        val result = mcpRepository.removeUserVars(serverName)
        if (result is Result.Error) {
            Logger.d(result.exception) { "Failed to remove MCP variables: ${result.message}" }
            return false
        }
        if (serverName in handle.state.effectiveMcpServerNames) toggleMcpServer(serverName)
        load()
        return true
    }

    /** Never cancels: a closed or refused sign-in leaves its flow pending, shown as connecting, as on web. */
    fun onSignInEnded() {
        load()
    }

    /** The user's "Cancel sign-in" (web's `cancelOAuthFlow`); reloads whether or not it landed. */
    fun cancelSignIn(serverName: String, onFailed: () -> Unit) {
        if (signInBusy) return
        if (selectOnConnect == serverName) selectOnConnect = null
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
    userVars = customUserVars,
)
