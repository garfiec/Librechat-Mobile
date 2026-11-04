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
)

/**
 * Drives the Sign in action on a live tool call that is waiting on an MCP OAuth
 * (`on_run_step_delta` with `delta.auth`; upstream `oauthPrompt: on_use` makes this mid-run).
 *
 * Kept out of `ChatViewModel`: it owns nothing of the conversation. The run resumes by itself once
 * the server's callback lands, and the step then completes like any other tool call, so a success
 * needs no follow-up here. The bind goes through [McpOAuthLauncher], the same gate Settings uses,
 * and happens at the tap: the CSRF cookie lives ten minutes and a later prompt replaces it.
 */
class McpSignInViewModel(
    private val launcher: McpOAuthLauncher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpSignInUiState())
    val uiState: StateFlow<McpSignInUiState> = _uiState.asStateFlow()

    fun signIn(serverName: String, authorizationUrl: String) {
        if (_uiState.value.preparingServer != null || _uiState.value.launch != null) return
        _uiState.update { it.copy(preparingServer = serverName, failed = false) }
        viewModelScope.launch {
            when (val result = launcher.prepare(serverName, authorizationUrl)) {
                is Result.Success -> _uiState.update { it.copy(preparingServer = null, launch = result.data) }
                is Result.Error -> _uiState.update { it.copy(preparingServer = null, failed = true) }
                Result.Loading -> Unit
            }
        }
    }

    /** The system browser was opened for an [McpOAuthLaunch.External] sign-in. */
    fun onLaunched() {
        _uiState.update { it.copy(launch = null) }
    }

    /** The in-app sign-in ended. A refused callback is reported; closing the page is not. */
    fun onOutcome(outcome: McpOAuthOutcome) {
        _uiState.update { it.copy(launch = null, failed = outcome is McpOAuthOutcome.Error) }
    }

    fun dismissFailure() {
        _uiState.update { it.copy(failed = false) }
    }
}
