package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus

@Immutable
data class McpServerDisplayData(
    val name: String,
    val title: String?,
    val description: String?,
    val status: McpDisplayStatus,
    val requiresOAuth: Boolean = false,
    /** Any server's sign-in reconnect or cancel is out; sign-in taps would be ignored, so none is offered. */
    val signInBusy: Boolean = false,
) {
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: name
}
