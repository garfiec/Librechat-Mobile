package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.pullrequest.PullRequestProvider

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

/** Upstream `PROVIDER_NAMES`: brand names, the same in every language. */
fun PullRequestProvider.displayName(): String = when (this) {
    PullRequestProvider.GITHUB -> "GitHub"
    PullRequestProvider.GITLAB -> "GitLab"
    PullRequestProvider.BITBUCKET -> "Bitbucket"
}
