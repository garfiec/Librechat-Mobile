package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch

@Immutable
data class McpSignInUiState(
    /** The server whose sign-in is being bound; its card's button shows progress meanwhile. */
    val preparingServer: String? = null,
    /** A bound sign-in to open: the sign-in dialog, or the system browser on an older server. */
    val launch: McpOAuthLaunch? = null,
    /** The last attempt failed; the screen shows it once and calls `McpSignInViewModel.dismissFailure`. */
    val failed: Boolean = false,
    /** The sign-in went to the system browser; the next return to the app ends it (`McpSignInViewModel.onReturned`). */
    val awaitingReturn: Boolean = false,
    /** A sign-in ended, however it ended: the chat re-reads its MCP statuses once and calls `McpSignInViewModel.consumeEnded`. */
    val ended: Boolean = false,
)
