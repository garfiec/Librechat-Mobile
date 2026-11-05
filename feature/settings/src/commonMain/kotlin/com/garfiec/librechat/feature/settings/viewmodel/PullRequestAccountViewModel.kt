package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.accountTransitions
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PullRequestConnectFailure
import com.garfiec.librechat.core.data.repository.PullRequestConnectionRepository
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.model.pullrequest.PullRequestConnection
import com.garfiec.librechat.core.model.pullrequest.PullRequestProvider
import com.garfiec.librechat.core.model.pullrequest.pullRequestConnectionKnowable
import com.garfiec.librechat.core.model.pullrequest.showsPullRequestAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the account row says about the connection. */
enum class PullRequestAccountStatus { CHECKING, NOT_CONNECTED, CONNECTED, NOT_IN_USE, ERROR }

/** A one-shot message for the page's snackbar. */
enum class PullRequestAccountNotice { CONNECT_FAILED, NOT_AVAILABLE }

@Immutable
data class PullRequestAccountUiState(
    /** Upstream's `hasPullRequestConnect`: a grant to remove, or an account the server offers to connect. */
    val visible: Boolean = false,
    val status: PullRequestAccountStatus = PullRequestAccountStatus.CHECKING,
    /** The provider the row names; null for a grant whose app the server no longer has. */
    val provider: PullRequestProvider? = null,
    val connecting: Boolean = false,
    /** The in-app sign-in on screen, or null. */
    val signIn: McpOAuthLaunch.InApp? = null,
    val confirmingDisconnect: Boolean = false,
    val disconnecting: Boolean = false,
    /** Shown inside the disconnect dialog, which stays open. */
    val disconnectFailed: Boolean = false,
    val notice: PullRequestAccountNotice? = null,
)

/**
 * Settings › Account › "Pull request account" (v0.8.9-rc1, upstream `ConnectedAccounts`). The status is
 * read when the tab opens and on every return to it; connecting runs the provider's sign-in in the app
 * (the callback needs the cookies the connect response set), and a disconnect asks first. Any change of
 * credential makes every cached pull request lookup ask again (`PullRequestConnectionRepository`).
 */
class PullRequestAccountViewModel(
    private val repository: PullRequestConnectionRepository,
    private val configRepository: ConfigRepository,
    activeAccountProvider: ActiveAccountProvider,
) : ViewModel() {

    private class Local(
        val connection: PullRequestConnection? = null,
        val statusFailed: Boolean = false,
        /** Connect answered 404: nothing to connect on this server for the rest of the session. */
        val unavailable: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private val actions = MutableStateFlow(PullRequestAccountUiState())
    private var statusJob: Job? = null

    private val knowable = combine(configRepository.startupConfig, configRepository.detectedBackendVersion) { config, version ->
        pullRequestConnectionKnowable(config, version)
    }.distinctUntilChanged()

    val uiState: StateFlow<PullRequestAccountUiState> = combine(
        configRepository.startupConfig,
        knowable,
        local,
        actions,
    ) { config, canAsk, known, action ->
        val connection = known.connection
        val connectProvider = config?.pullRequestsConnect
        val status = when {
            connection?.connected == true && !connection.configured -> PullRequestAccountStatus.NOT_IN_USE
            connection?.connected == true -> PullRequestAccountStatus.CONNECTED
            known.statusFailed -> PullRequestAccountStatus.ERROR
            connection != null -> PullRequestAccountStatus.NOT_CONNECTED
            else -> PullRequestAccountStatus.CHECKING
        }
        // The configured provider until the status names one; after it, a grant whose app is gone
        // may belong to any provider, so the row names none.
        val providerKey = if (connection != null) connection.provider else connectProvider
        action.copy(
            visible = canAsk && !known.unavailable && showsPullRequestAccount(connectProvider, connection),
            status = status,
            provider = providerKey?.let(::providerOf),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PullRequestAccountUiState())

    init {
        viewModelScope.launch { knowable.collect { if (it) refresh() } }
        viewModelScope.launch {
            activeAccountProvider.accountTransitions().collect {
                statusJob?.cancel()
                local.value = Local()
                actions.value = PullRequestAccountUiState()
                refresh()
            }
        }
    }

    /** Reads the status again: on returning to the tab, and the row's Retry. */
    fun refresh() {
        if (!pullRequestConnectionKnowable(configRepository.startupConfig.value, configRepository.detectedBackendVersion.value)) {
            return
        }
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            when (val result = repository.status()) {
                is Result.Success -> {
                    local.update { Local(connection = result.data, unavailable = it.unavailable) }
                    // A disconnect that failed may still have ended the connection: then the dialog
                    // closes as on success.
                    if (!result.data.connected) {
                        actions.update {
                            if (it.disconnectFailed) it.copy(confirmingDisconnect = false, disconnectFailed = false) else it
                        }
                    }
                }
                // A grant known to be removable stays removable while a later read fails.
                is Result.Error -> local.update { Local(connection = it.connection, statusFailed = true, unavailable = it.unavailable) }
                Result.Loading -> Unit
            }
        }
    }

    /** The row's tap: Connect, Disconnect (asks first), or Retry, by status. */
    fun onRowClick() {
        when (uiState.value.status) {
            PullRequestAccountStatus.NOT_CONNECTED -> connect()
            PullRequestAccountStatus.CONNECTED, PullRequestAccountStatus.NOT_IN_USE ->
                actions.update { it.copy(confirmingDisconnect = true, disconnectFailed = false) }
            PullRequestAccountStatus.ERROR -> refresh()
            PullRequestAccountStatus.CHECKING -> Unit
        }
    }

    private fun connect() {
        if (actions.value.connecting || actions.value.signIn != null) return
        val provider = uiState.value.provider ?: return
        actions.update { it.copy(connecting = true) }
        viewModelScope.launch {
            when (val result = repository.connect(provider.displayName())) {
                is Result.Success -> actions.update { it.copy(connecting = false, signIn = result.data.launch) }
                is Result.Error -> {
                    val unavailable = PullRequestConnectionRepository.failureOf(result) == PullRequestConnectFailure.NOT_CONFIGURED
                    if (unavailable) local.update { Local(it.connection, it.statusFailed, unavailable = true) }
                    actions.update {
                        it.copy(
                            connecting = false,
                            notice = if (unavailable) PullRequestAccountNotice.NOT_AVAILABLE else PullRequestAccountNotice.CONNECT_FAILED,
                        )
                    }
                }
                Result.Loading -> actions.update { it.copy(connecting = false) }
            }
        }
    }

    /**
     * The sign-in page ended. A success re-reads the status, which flips the row. A closed page reads
     * it too: the grant may have landed before the user closed it. An error page says so.
     */
    fun onSignInOutcome(outcome: McpOAuthOutcome) {
        actions.update {
            it.copy(
                signIn = null,
                notice = if (outcome is McpOAuthOutcome.Error) PullRequestAccountNotice.CONNECT_FAILED else it.notice,
            )
        }
        refresh()
    }

    fun confirmDisconnect() {
        if (actions.value.disconnecting) return
        actions.update { it.copy(disconnecting = true, disconnectFailed = false) }
        viewModelScope.launch {
            val result = repository.disconnect()
            if (result is Result.Success) {
                actions.update { it.copy(disconnecting = false, confirmingDisconnect = false) }
            } else {
                actions.update { it.copy(disconnecting = false, disconnectFailed = true) }
            }
            refresh()
        }
    }

    /** The dialog's Cancel; ignored while the request is out, as it can't be taken back. */
    fun dismissDisconnect() {
        if (actions.value.disconnecting) return
        actions.update { it.copy(confirmingDisconnect = false, disconnectFailed = false) }
    }

    fun consumeNotice() {
        actions.update { it.copy(notice = null) }
    }

    private companion object {
        fun providerOf(key: String): PullRequestProvider = when (key) {
            "gitlab" -> PullRequestProvider.GITLAB
            "bitbucket" -> PullRequestProvider.BITBUCKET
            else -> PullRequestProvider.GITHUB
        }
    }
}

/** Upstream `PROVIDER_NAMES`: brand names, the same in every language. */
fun PullRequestProvider.displayName(): String = when (this) {
    PullRequestProvider.GITHUB -> "GitHub"
    PullRequestProvider.GITLAB -> "GitLab"
    PullRequestProvider.BITBUCKET -> "Bitbucket"
}
