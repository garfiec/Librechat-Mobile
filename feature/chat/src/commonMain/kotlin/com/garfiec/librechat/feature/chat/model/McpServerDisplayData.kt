package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpUserVar

@Immutable
data class McpServerDisplayData(
    val name: String,
    val title: String?,
    val description: String?,
    val status: McpDisplayStatus,
    val requiresOAuth: Boolean = false,
    /** Any server's sign-in reconnect or cancel is out; sign-in taps would be ignored, so none is offered. */
    val signInBusy: Boolean = false,
    /** What the user must enter before it connects; the variables sheet's fields. */
    val userVars: List<McpUserVar> = emptyList(),
) {
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: name

    /** Needs sign-in, or an OAuth server that failed: a tap signs in (web's MCP palette rule). */
    val signsInOnTap: Boolean
        get() = status == McpDisplayStatus.NEEDS_SIGN_IN || (requiresOAuth && status == McpDisplayStatus.FAILED)

    /** Owed variables the sheet can take: a tap opens it, and its row shows "Set up". */
    val setsUpOnTap: Boolean
        get() = status == McpDisplayStatus.NEEDS_SETUP && userVars.isNotEmpty()

    /**
     * An OAuth flow is open, maybe finished in the browser, maybe abandoned. The app never cancels
     * it by itself: a run elsewhere may be waiting on the same flow.
     */
    val signInPending: Boolean
        get() = requiresOAuth && status == McpDisplayStatus.CONNECTING
}
