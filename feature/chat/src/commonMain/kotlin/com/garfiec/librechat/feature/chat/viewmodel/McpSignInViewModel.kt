package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpOAuthLauncher
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class McpSignInUiState(
    /** The server whose sign-in is being bound; its card's button shows progress meanwhile. */
    val preparingServer: String? = null,
    /** A bound sign-in to open: the sign-in dialog, or the system browser on an older server. */
    val launch: McpOAuthLaunch? = null,
    /** The last attempt failed; the screen shows it once and calls [McpSignInViewModel.dismissFailure]. */
    val failed: Boolean = false,
    /** The sign-in went to the system browser; the next return to the app ends it ([McpSignInViewModel.onReturned]). */
    val awaitingReturn: Boolean = false,
    /** A sign-in ended, however it ended: the chat re-reads its MCP statuses once and calls [McpSignInViewModel.consumeEnded]. */
    val ended: Boolean = false,
)

/**
 * Drives the Sign in action on a live tool call that is waiting on an MCP OAuth
 * (`on_run_step_delta` with `delta.auth`; upstream `oauthPrompt: on_use` makes this mid-run), and on
 * a settings chip whose server needs sign-in (the chat's reconnect hands it the link).
 *
 * Kept out of `ChatViewModel`: it owns nothing of the conversation. A mid-run sign-in's run resumes
 * by itself once the server's callback lands; [McpSignInUiState.ended] is only so the chat's server
 * statuses (the settings chips' dots) catch up. The bind goes through [McpOAuthLauncher], the same
 * gate Settings uses, and happens at the tap: the CSRF cookie lives ten minutes and a later prompt
 * replaces it.
 */
class McpSignInViewModel(
    private val launcher: McpOAuthLauncher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpSignInUiState())
    val uiState: StateFlow<McpSignInUiState> = _uiState.asStateFlow()

    /**
     * Returns false, opening nothing, while another sign-in is binding or on screen; no
     * [McpSignInUiState.ended] will come for this one, so the caller reloads itself.
     */
    fun signIn(serverName: String, authorizationUrl: String): Boolean {
        if (_uiState.value.preparingServer != null || _uiState.value.launch != null) return false
        _uiState.update { it.copy(preparingServer = serverName, failed = false, awaitingReturn = false) }
        viewModelScope.launch {
            when (val result = launcher.prepare(serverName, authorizationUrl)) {
                is Result.Success -> _uiState.update { it.copy(preparingServer = null, launch = result.data) }
                is Result.Error -> _uiState.update {
                    it.copy(preparingServer = null, failed = true, ended = true)
                }
                Result.Loading -> Unit
            }
        }
        return true
    }

    /** A browser sign-in ends on the next return ([onReturned]); one whose browser never opened ends now. */
    fun onLaunched(opened: Boolean) {
        _uiState.update {
            if (opened) {
                it.copy(launch = null, awaitingReturn = true)
            } else {
                it.copy(launch = null, awaitingReturn = false, ended = true)
            }
        }
    }

    /**
     * A sign-in's [McpSignInUiState.ended] will re-read the statuses on this return; true whichever
     * of the two ON_RESUME observers runs first.
     */
    val endsOnReturn: Boolean get() = _uiState.value.let { it.awaitingReturn || it.ended }

    fun onReturned() {
        if (!_uiState.value.awaitingReturn) return
        _uiState.update { it.copy(awaitingReturn = false, ended = true) }
    }

    /** The in-app sign-in ended. A refused callback is reported; closing the page is not. */
    fun onOutcome(outcome: McpOAuthOutcome) {
        _uiState.update { it.copy(launch = null, failed = outcome is McpOAuthOutcome.Error, ended = true) }
    }

    fun dismissFailure() {
        _uiState.update { it.copy(failed = false) }
    }

    fun consumeEnded() {
        _uiState.update { it.copy(ended = false) }
    }
}
