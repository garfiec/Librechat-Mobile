package com.garfiec.librechat.core.ui.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.mcp_status_connected
import com.garfiec.librechat.core.ui.resources.mcp_status_connecting
import com.garfiec.librechat.core.ui.resources.mcp_status_disconnected
import com.garfiec.librechat.core.ui.resources.mcp_status_failed
import com.garfiec.librechat.core.ui.resources.mcp_status_needs_setup
import com.garfiec.librechat.core.ui.resources.mcp_status_needs_sign_in
import com.garfiec.librechat.core.ui.resources.mcp_status_on_demand
import com.garfiec.librechat.core.ui.theme.StatusColors
import com.garfiec.librechat.core.ui.theme.statusColors
import org.jetbrains.compose.resources.StringResource

/** [McpDisplayStatus.UNKNOWN] reads as not connected; a screen that shows nothing until a status is in leaves it out itself. */
fun mcpStatusLabel(status: McpDisplayStatus): StringResource = when (status) {
    McpDisplayStatus.CONNECTED -> Res.string.mcp_status_connected
    McpDisplayStatus.CONNECTING -> Res.string.mcp_status_connecting
    McpDisplayStatus.NEEDS_SIGN_IN -> Res.string.mcp_status_needs_sign_in
    McpDisplayStatus.NEEDS_SETUP -> Res.string.mcp_status_needs_setup
    McpDisplayStatus.ON_DEMAND -> Res.string.mcp_status_on_demand
    McpDisplayStatus.FAILED -> Res.string.mcp_status_failed
    McpDisplayStatus.DISCONNECTED, McpDisplayStatus.UNKNOWN -> Res.string.mcp_status_disconnected
}

/** Always shown with [mcpStatusLabel]. */
fun McpDisplayStatus.tint(colors: StatusColors): Color = when (this) {
    McpDisplayStatus.CONNECTED -> colors.success
    McpDisplayStatus.CONNECTING -> colors.info
    McpDisplayStatus.NEEDS_SIGN_IN, McpDisplayStatus.NEEDS_SETUP -> colors.warning
    McpDisplayStatus.FAILED -> colors.error
    McpDisplayStatus.ON_DEMAND, McpDisplayStatus.DISCONNECTED, McpDisplayStatus.UNKNOWN -> colors.neutral
}

@Composable
@ReadOnlyComposable
fun mcpStatusColor(status: McpDisplayStatus): Color = status.tint(statusColors())
