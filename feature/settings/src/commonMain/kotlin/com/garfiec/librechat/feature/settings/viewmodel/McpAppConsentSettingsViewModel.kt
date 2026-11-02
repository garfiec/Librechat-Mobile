package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.data.datastore.McpAppConsentStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class McpAppConsentSettingsUiState(
    /** Whether this build asks before running an MCP App at all; the row hides when it doesn't. */
    val shown: Boolean = false,
    /** MCP servers whose Apps open without asking, for the signed-in account. */
    val allowedServers: Int = 0,
)

/**
 * Settings › Chat's MCP apps row: how many servers this account let open Apps without asking, and
 * the reset that makes each ask again ("Don't ask again" in the chat's consent dialog).
 */
class McpAppConsentSettingsViewModel(
    private val consentStore: McpAppConsentStore,
    channelPolicy: ChannelPolicy,
) : ViewModel() {

    val uiState: StateFlow<McpAppConsentSettingsUiState> = consentStore.allowedServers
        .map { McpAppConsentSettingsUiState(shown = channelPolicy.mcpAppsNeedConsent, allowedServers = it.size) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            McpAppConsentSettingsUiState(shown = channelPolicy.mcpAppsNeedConsent),
        )

    fun revokeAll() {
        viewModelScope.launch { consentStore.revokeAll() }
    }
}
