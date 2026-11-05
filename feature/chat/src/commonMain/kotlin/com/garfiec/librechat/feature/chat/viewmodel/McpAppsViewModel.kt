package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.data.datastore.McpAppLocationConsent
import com.garfiec.librechat.core.data.datastore.McpAppLocationConsentStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppAction
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppHost
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppHostCallbacks
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppNotice
import com.garfiec.librechat.feature.chat.components.mcpapp.mcpAppLocationSupported
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * One open App. [id] is unique per host: Retry replaces the session with a new id (a new host and
 * a new web view), so a web view is never handed to a host it wasn't built for.
 */
@Immutable
data class McpAppSession(val id: Long, val occurrence: McpAppOccurrence, val host: McpAppHost, val attempt: Int)

/**
 * The open Apps. [sessions] in the order they opened; [panelId] is the App the full-screen panel
 * shows (null: the panel is down and every App is inline in its card); [inlineHeights] the slot
 * each App's card froze at (dp), kept here because the card leaves composition on scroll;
 * [evicted] the occurrences memory pressure tore down, whose cards offer "Tap to resume".
 */
@Immutable
data class McpAppsState(
    val sessions: List<McpAppSession> = emptyList(),
    val panelId: Long? = null,
    val inlineHeights: Map<Long, Int> = emptyMap(),
    val evicted: Set<String> = emptySet(),
) {
    fun sessionFor(occurrenceKey: String): McpAppSession? = sessions.firstOrNull { it.occurrence.key == occurrenceKey }
    val panelSession: McpAppSession? get() = panelId?.let { id -> sessions.firstOrNull { it.id == id } }
}

/**
 * An App's request for the device location, while the user decides: first this app's own question
 * for [serverName] ([Stage.CONSENT], only when never answered), then the OS permission. [sessionId]
 * is the App that asked; only it can be answered or refused by it closing. [requestId] tells one
 * request from the next, so a repeated request from the same App is a new question.
 */
@Immutable
data class McpAppLocationRequest(
    val sessionId: Long,
    val serverName: String,
    val stage: Stage,
    val requestId: Long = 0,
) {
    enum class Stage { CONSENT, OS_PERMISSION }
}

/** A legacy MCP-UI resource's action, waiting for the user to send it as a message or not. */
@Immutable
data class LegacyUiMessageRequest(val id: Long, val text: String)

/** One-shot things the screen shows on the chat's snackbar or opens. */
sealed interface McpAppEvent {
    data class Notice(val notice: McpAppNotice, val serverName: String) : McpAppEvent
    data class OpenLink(val url: String) : McpAppEvent

    /** "Open app" with every slot taken: upstream's capacity line, with "Show apps". */
    data object AtCapacity : McpAppEvent

    /** An App the user can't see wants something decided: "<server> wants to …", with "Show". */
    data class Waiting(val sessionId: Long, val serverName: String, val isMessage: Boolean) : McpAppEvent
}

/**
 * Owns the open MCP Apps. Kept out of `ChatViewModel` and out of `ChatUiState`: an App is a view
 * beside the chat, not conversation state, and process death simply closes it. Each host speaks the
 * protocol for its App; this decides how many exist, which is in the panel, and which one's
 * question may show (only one the user can see, [McpAppSessionRules.foreground]).
 */
class McpAppsViewModel(
    private val repository: McpAppsRepository,
    private val appInfo: AppInfo,
    configRepository: ConfigRepository,
    private val locationConsent: McpAppLocationConsentStore,
) : ViewModel() {

    /**
     * Set when an App route says Apps are off; cleared by the next config the server sends, equal or
     * not (`startupConfigRefreshes`: `startupConfig` would swallow an unchanged one).
     */
    private val disabledByServer = MutableStateFlow(false)

    /** The server's App policy, or [McpAppsPolicy.DISABLED] until its config says otherwise. */
    val policy: StateFlow<McpAppsPolicy> = combine(
        configRepository.startupConfig.map { it?.mcpAppsPolicy ?: McpAppsPolicy.DISABLED }.distinctUntilChanged(),
        disabledByServer,
    ) { resolved, disabled -> if (disabled) McpAppsPolicy.DISABLED else resolved }
        .stateIn(viewModelScope, SharingStarted.Eagerly, McpAppsPolicy.DISABLED)

    private val _state = MutableStateFlow(McpAppsState())
    val state: StateFlow<McpAppsState> = _state.asStateFlow()

    private val _visibleInline = MutableStateFlow<Set<Long>>(emptySet())

    private val _awaiting = MutableStateFlow<Set<Long>>(emptySet())

    /** Apps waiting on the user (an approval or a location question): their tab and card get a dot. */
    val awaiting: StateFlow<Set<Long>> = _awaiting.asStateFlow()

    /** The App whose question shows now, or null. */
    val presented: StateFlow<Long?> = combine(_state, _visibleInline, _awaiting) { state, visible, awaiting ->
        val ids = state.sessions.map { it.id }
        McpAppSessionRules.presented(ids, McpAppSessionRules.foreground(ids, state.panelId, visible), awaiting)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = Channel<McpAppEvent>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<McpAppEvent> = _events.receiveAsFlow()

    private val _locationRequest = MutableStateFlow<McpAppLocationRequest?>(null)

    /** The location question pending, or null. One at a time; it shows only while its App is presented. */
    val locationRequest: StateFlow<McpAppLocationRequest?> = _locationRequest.asStateFlow()
    private var locationReply: ((Boolean) -> Unit)? = null
    private var locationRequestCount = 0L

    private val _legacyMessage = MutableStateFlow<LegacyUiMessageRequest?>(null)

    /** The legacy MCP-UI message on screen for confirmation, or null. One at a time. */
    val legacyMessage: StateFlow<LegacyUiMessageRequest?> = _legacyMessage.asStateFlow()
    private var legacyReply: ((Boolean) -> Unit)? = null
    private var nextLegacyId = 0L

    private var nextSessionId = 1L
    private var lowRamDevice = false

    /** The panel's tabs, most recently shown last: where it opens and what shows after a close. */
    private val lastUsed = mutableListOf<Long>()
    private val watchers = mutableMapOf<Long, Job>()

    /**
     * A legacy resource asked to send [text] (web sends it at once; here the user confirms, since the
     * resource can fire one without a tap). A request while one is shown is refused.
     */
    fun requestLegacyMessage(text: String, reply: (sent: Boolean) -> Unit) {
        if (_legacyMessage.value != null || text.isBlank()) return reply(false)
        legacyReply = reply
        _legacyMessage.value = LegacyUiMessageRequest(nextLegacyId++, text)
    }

    /** The user's answer to the request with [id]; a stale answer is ignored. */
    fun answerLegacyMessage(id: Long, send: Boolean) {
        val request = _legacyMessage.value?.takeIf { it.id == id } ?: return
        val reply = legacyReply
        legacyReply = null
        _legacyMessage.value = null
        // The chat refuses while a reply runs, as for an App's message.
        val sent = send && (messageSink?.invoke(request.text) ?: false)
        reply?.invoke(sent)
    }

    /** Sends an App's approved message as a user turn; wired by the chat screen. */
    private var messageSink: ((String) -> Boolean)? = null
    private var hostContext: JsonObject = JsonObject(emptyMap())

    init {
        // A fresh config is the server speaking again: it lifts a "disabled" an App route reported.
        viewModelScope.launch {
            configRepository.startupConfigRefreshes.collect { disabledByServer.value = false }
        }
    }

    fun attachMessageSink(sink: ((String) -> Boolean)?) {
        messageSink = sink
    }

    /** Android's low-RAM flag: one App at a time there. */
    fun setLowRamDevice(lowRam: Boolean) {
        lowRamDevice = lowRam
    }

    /** How many Apps may be open at once now. */
    val viewCap: Int get() = McpAppSessionRules.viewCap(policy.value.maxActiveViews, lowRamDevice)

    /** The host context where App [id] is drawn now; also the starting context for the next App. */
    fun updateHostContext(id: Long, context: JsonObject) {
        hostContext = context
        session(id)?.host?.updateHostContext(context)
    }

    /** "Open app" on a row, or "Tap to resume" on an evicted one. Refused at the cap, with a notice. */
    fun open(occurrence: McpAppOccurrence) {
        if (!policy.value.enabled) return
        val existing = _state.value.sessionFor(occurrence.key)
        if (existing != null) return expand(existing.id)
        if (_state.value.sessions.size >= viewCap) {
            _events.trySend(McpAppEvent.AtCapacity)
            return
        }
        val session = newSession(occurrence, attempt = 0)
        _state.update { it.copy(sessions = it.sessions + session, evicted = it.evicted - occurrence.key) }
        startWatching(session)
        session.host.start()
    }

    /** Shows App [id] full screen (Expand, a tab, "Show"). */
    fun expand(id: Long) {
        if (session(id) == null) return
        lastUsed.remove(id)
        lastUsed += id
        _state.update { it.copy(panelId = id) }
    }

    /** Back on the panel: every App returns to its card and keeps running. */
    fun minimize() {
        _state.update { it.copy(panelId = null) }
    }

    /** "Show apps": the panel, on the App last shown there (else the first opened). */
    fun showApps() {
        val ids = _state.value.sessions.map { it.id }
        (lastUsed.lastOrNull { it in ids } ?: ids.firstOrNull())?.let(::expand)
    }

    /** The card of inline App [id] entered or left the viewport. */
    fun setInlineVisible(id: Long, visible: Boolean) {
        _visibleInline.update { if (visible) it + id else it - id }
    }

    /** Freezes App [id]'s inline slot at [heightDp], once: a slot that kept resizing would jump the list. */
    fun freezeInlineHeight(id: Long, heightDp: Int) {
        _state.update { state ->
            if (id in state.inlineHeights || state.sessions.none { it.id == id }) {
                state
            } else {
                state.copy(inlineHeights = state.inlineHeights + (id to heightDp))
            }
        }
    }

    fun retry(id: Long) {
        val current = session(id) ?: return
        // The old web view's question goes with it.
        refuseLocationOf(id)
        current.host.close()
        stopWatching(id)
        val replacement = newSession(current.occurrence, attempt = current.attempt + 1)
        for (i in lastUsed.indices) if (lastUsed[i] == id) lastUsed[i] = replacement.id
        _visibleInline.update { it - id }
        _state.update { state ->
            state.copy(
                sessions = state.sessions.map { if (it.id == id) replacement else it },
                panelId = if (state.panelId == id) replacement.id else state.panelId,
                inlineHeights = state.inlineHeights - id,
            )
        }
        startWatching(replacement)
        replacement.host.start()
    }

    /** Close X: tears App [id] down. The panel moves to another open App, or goes when none is left. */
    fun close(id: Long) {
        val closing = session(id) ?: return
        // Whatever the closing App was waiting on is refused, so its WebView isn't left hanging.
        refuseLocationOf(id)
        closing.host.close()
        stopWatching(id)
        _visibleInline.update { it - id }
        _state.update { state ->
            val ids = state.sessions.map { it.id }
            state.copy(
                sessions = state.sessions.filterNot { it.id == id },
                panelId = McpAppSessionRules.panelAfterClose(ids, id, state.panelId, lastUsed),
                inlineHeights = state.inlineHeights - id,
            )
        }
        lastUsed.remove(id)
    }

    /** Leaving the chat or switching account: every App goes. */
    fun closeAll() {
        _state.value.sessions.map { it.id }.forEach(::close)
        _state.update { it.copy(panelId = null) }
    }

    /**
     * The system is short of memory: tear down every App the user can't see and that waits on
     * nothing. Their cards show the last picture and "Tap to resume" (which reloads).
     */
    fun onMemoryPressure() {
        val state = _state.value
        val ids = state.sessions.map { it.id }
        val foreground = McpAppSessionRules.foreground(ids, state.panelId, _visibleInline.value)
        val evict = McpAppSessionRules.evictable(ids, foreground, _awaiting.value)
        if (evict.isEmpty()) return
        val keys = state.sessions.filter { it.id in evict }.map { it.occurrence.key }
        evict.forEach(::close)
        _state.update { it.copy(evicted = it.evicted + keys) }
    }

    /** App [id]'s web view went (its renderer died): it shows as failed with Retry. */
    fun onViewLost(id: Long) {
        refuseLocationOf(id)
        session(id)?.host?.close(viewLost = true)
    }

    /** The user's answer to "Allow <server> to use your approximate location?". */
    fun answerLocationConsent(allow: Boolean) {
        val request = _locationRequest.value?.takeIf { it.stage == McpAppLocationRequest.Stage.CONSENT } ?: return
        viewModelScope.launch {
            locationConsent.set(
                request.serverName,
                if (allow) McpAppLocationConsent.ALLOWED else McpAppLocationConsent.REFUSED,
            )
        }
        if (allow) {
            _locationRequest.value = request.copy(stage = McpAppLocationRequest.Stage.OS_PERMISSION)
        } else {
            answerLocation(false)
        }
    }

    /** The OS permission's outcome (granted now or earlier). */
    fun answerLocationPermission(granted: Boolean) {
        if (_locationRequest.value?.stage != McpAppLocationRequest.Stage.OS_PERMISSION) return
        answerLocation(granted)
    }

    private fun refuseLocationOf(id: Long) {
        if (locationOwner == id) answerLocation(false)
    }

    private var locationOwner: Long? = null

    private fun answerLocation(allowed: Boolean) {
        val reply = locationReply
        val owner = locationOwner
        locationReply = null
        locationOwner = null
        _locationRequest.value = null
        owner?.let(::refreshAwaiting)
        reply?.invoke(allowed)
    }

    private fun session(id: Long): McpAppSession? = _state.value.sessions.firstOrNull { it.id == id }

    private fun newSession(occurrence: McpAppOccurrence, attempt: Int): McpAppSession {
        val id = nextSessionId++
        val host = McpAppHost(
            resource = occurrence.resource,
            policy = policy.value,
            repository = repository,
            scope = viewModelScope,
            callbacks = SessionCallbacks(id),
            hostInfoVersion = appInfo.versionName,
            hostContext = hostContext,
        )
        return McpAppSession(id, occurrence, host, attempt)
    }

    /** Follows the App's approval queue: the dot, and a notice when it waits where the user can't see. */
    private fun startWatching(session: McpAppSession) {
        watchers[session.id] = viewModelScope.launch {
            var previous: McpAppAction? = null
            session.host.pendingAction.collect { action ->
                refreshAwaiting(session.id)
                if (action != null && action != previous && !isForeground(session.id)) {
                    _events.trySend(
                        McpAppEvent.Waiting(session.id, action.serverName, isMessage = action is McpAppAction.Message),
                    )
                }
                previous = action
            }
        }
    }

    private fun stopWatching(id: Long) {
        watchers.remove(id)?.cancel()
        _awaiting.update { it - id }
    }

    private fun refreshAwaiting(id: Long) {
        val waits = session(id)?.host?.pendingAction?.value != null || locationOwner == id
        _awaiting.update { if (waits) it + id else it - id }
    }

    private fun isForeground(id: Long): Boolean {
        val state = _state.value
        return id in McpAppSessionRules.foreground(state.sessions.map { it.id }, state.panelId, _visibleInline.value)
    }

    /** One App's link to the screen: everything it asks is about its own session. */
    private inner class SessionCallbacks(private val id: Long) : McpAppHostCallbacks {
        override fun sendMessage(text: String): Boolean = messageSink?.invoke(text) ?: false

        override fun openLink(url: String) {
            _events.trySend(McpAppEvent.OpenLink(url))
        }

        override fun notice(notice: McpAppNotice, serverName: String) {
            if (notice == McpAppNotice.APPS_DISABLED) disabledByServer.update { true }
            _events.trySend(McpAppEvent.Notice(notice, serverName))
        }

        override fun close() {
            this@McpAppsViewModel.close(id)
        }

        // Out of the reply's way, still running: the panel goes, the App stays in its card.
        override fun minimize() {
            if (_state.value.panelId == id) this@McpAppsViewModel.minimize()
        }

        override fun requestDisplayMode(mode: String): String {
            val state = _state.value
            val ids = state.sessions.map { it.id }
            val foreground = McpAppSessionRules.foreground(ids, state.panelId, _visibleInline.value)
            when (McpAppSessionRules.modeChange(id, mode, state.panelId, foreground)) {
                McpAppSessionRules.ModeChange.Expand -> expand(id)
                McpAppSessionRules.ModeChange.Collapse -> this@McpAppsViewModel.minimize()
                McpAppSessionRules.ModeChange.None -> Unit
            }
            return McpAppSessionRules.displayMode(id, _state.value.panelId)
        }

        override suspend fun mayOfferLocation(serverName: String): Boolean =
            mcpAppLocationSupported && locationConsent.consent(serverName) != McpAppLocationConsent.REFUSED

        override fun withdrawLocation() = refuseLocationOf(id)

        override fun requestLocation(serverName: String, reply: (Boolean) -> Unit) {
            val owner = locationOwner
            // Another App's question stays; this one is refused rather than replacing it.
            if (owner != null && owner != id) return reply(false)
            // A newer request from the same App replaces its older one, which is refused.
            answerLocation(false)
            locationReply = reply
            locationOwner = id
            val requestId = ++locationRequestCount
            refreshAwaiting(id)
            viewModelScope.launch {
                when (locationConsent.consent(serverName)) {
                    McpAppLocationConsent.REFUSED -> if (locationReply === reply) answerLocation(false)
                    McpAppLocationConsent.ALLOWED -> if (locationReply === reply) {
                        _locationRequest.value =
                            McpAppLocationRequest(id, serverName, McpAppLocationRequest.Stage.OS_PERMISSION, requestId)
                    }
                    McpAppLocationConsent.NOT_ASKED -> if (locationReply === reply) {
                        _locationRequest.value = McpAppLocationRequest(id, serverName, McpAppLocationRequest.Stage.CONSENT, requestId)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        answerLocation(false)
        legacyReply?.invoke(false)
        legacyReply = null
        _state.value.sessions.forEach { it.host.close() }
        super.onCleared()
    }
}
