package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.data.datastore.McpAppConsentStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppHost
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppHostCallbacks
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppNotice
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

/** The App the panel shows. [attempt] bumps on Retry, which builds a fresh host and WebView. */
@Immutable
data class McpAppSession(val occurrence: McpAppOccurrence, val host: McpAppHost, val attempt: Int)

/** One-shot things the screen shows on the chat's snackbar or opens. */
sealed interface McpAppEvent {
    data class Notice(val notice: McpAppNotice, val serverName: String) : McpAppEvent
    data class OpenLink(val url: String) : McpAppEvent
}

/**
 * Owns the one open MCP App and its first-open consent. Kept out of `ChatViewModel` and out of
 * `ChatUiState`: an App is a panel over the chat, not conversation state, and process death simply
 * closes it. The host speaks the protocol; this decides when one exists.
 */
class McpAppsViewModel(
    private val repository: McpAppsRepository,
    private val consentStore: McpAppConsentStore,
    private val channelPolicy: ChannelPolicy,
    private val appInfo: AppInfo,
    configRepository: ConfigRepository,
) : ViewModel() {

    /** Set when an App route says Apps are off; cleared by the next config the server sends. */
    private val disabledByServer = MutableStateFlow(false)

    /** The server's App policy, or [McpAppsPolicy.DISABLED] until its config says otherwise. */
    val policy: StateFlow<McpAppsPolicy> = combine(
        configRepository.startupConfig.map { it?.mcpAppsPolicy ?: McpAppsPolicy.DISABLED }.distinctUntilChanged(),
        disabledByServer,
    ) { resolved, disabled -> if (disabled) McpAppsPolicy.DISABLED else resolved }
        .stateIn(viewModelScope, SharingStarted.Eagerly, McpAppsPolicy.DISABLED)

    private val _session = MutableStateFlow<McpAppSession?>(null)
    val session: StateFlow<McpAppSession?> = _session.asStateFlow()

    private val _consentPrompt = MutableStateFlow<McpAppOccurrence?>(null)

    /** The App waiting on the first-open consent dialog, or null. */
    val consentPrompt: StateFlow<McpAppOccurrence?> = _consentPrompt.asStateFlow()

    private val _events = Channel<McpAppEvent>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<McpAppEvent> = _events.receiveAsFlow()

    /** Sends an App's approved message as a user turn; wired by the chat screen. */
    private var messageSink: ((String) -> Boolean)? = null
    private var hostContext: JsonObject = JsonObject(emptyMap())

    init {
        // A fresh config is the server speaking again: it lifts a "disabled" an App route reported.
        viewModelScope.launch {
            configRepository.startupConfig.collect { disabledByServer.value = false }
        }
    }

    fun attachMessageSink(sink: ((String) -> Boolean)?) {
        messageSink = sink
    }

    fun updateHostContext(context: JsonObject) {
        hostContext = context
        _session.value?.host?.updateHostContext(context)
    }

    /** "Open app" on a row. Asks first on a server this account hasn't allowed, when the channel asks. */
    fun open(occurrence: McpAppOccurrence) {
        if (!policy.value.enabled || _session.value != null || _consentPrompt.value != null) return
        val serverName = occurrence.resource.serverName.orEmpty()
        viewModelScope.launch {
            if (channelPolicy.mcpAppsNeedConsent && !consentStore.isAllowed(serverName)) {
                _consentPrompt.value = occurrence
            } else {
                start(occurrence, attempt = 0)
            }
        }
    }

    fun confirmConsent(remember: Boolean) {
        val occurrence = _consentPrompt.value ?: return
        _consentPrompt.value = null
        viewModelScope.launch {
            if (remember) consentStore.allow(occurrence.resource.serverName.orEmpty())
            start(occurrence, attempt = 0)
        }
    }

    fun dismissConsent() {
        _consentPrompt.value = null
    }

    fun retry() {
        val current = _session.value ?: return
        current.host.close()
        start(current.occurrence, attempt = current.attempt + 1)
    }

    fun close() {
        _session.value?.host?.close()
        _session.value = null
    }

    private fun start(occurrence: McpAppOccurrence, attempt: Int) {
        val host = McpAppHost(
            resource = occurrence.resource,
            policy = policy.value,
            repository = repository,
            scope = viewModelScope,
            callbacks = callbacks,
            hostInfoVersion = appInfo.versionName,
            hostContext = hostContext,
        )
        _session.value = McpAppSession(occurrence, host, attempt)
        host.start()
    }

    private val callbacks = object : McpAppHostCallbacks {
        override fun sendMessage(text: String): Boolean = messageSink?.invoke(text) ?: false

        override fun openLink(url: String) {
            _events.trySend(McpAppEvent.OpenLink(url))
        }

        override fun notice(notice: McpAppNotice, serverName: String) {
            if (notice == McpAppNotice.APPS_DISABLED) disabledByServer.update { true }
            _events.trySend(McpAppEvent.Notice(notice, serverName))
        }

        override fun close() {
            this@McpAppsViewModel.close()
        }
    }

    override fun onCleared() {
        _session.value?.host?.close()
        super.onCleared()
    }
}
