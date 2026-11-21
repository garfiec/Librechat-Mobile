package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue

/**
 * The Sign in action of a live tool call waiting on an MCP OAuth, and of a settings chip whose
 * server needs sign-in, and of a live tool call waiting on an agent action's OAuth ([signInAction]).
 * [preparingServer] is the server (or action id) whose sign-in is being bound, so only its button
 * shows progress.
 */
@Immutable
data class McpSignIn(
    val preparingServer: String?,
    /** Whether it opened: false while another sign-in is binding or on screen. */
    val signIn: (serverName: String, authorizationUrl: String) -> Boolean,
    val signInAction: (actionId: String, authorizationUrl: String) -> Boolean,
)

/**
 * Provided by [ChatRootContent] from `rememberMcpSignIn`. Null where nothing can sign in (the shared-chat
 * viewer, previews); the card then shows the prompt without an action.
 */
val LocalMcpSignIn = compositionLocalOf<McpSignIn?> { null }
