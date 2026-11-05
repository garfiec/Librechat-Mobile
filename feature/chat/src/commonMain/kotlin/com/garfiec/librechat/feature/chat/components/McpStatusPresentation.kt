package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.setting_chip_connected
import com.garfiec.librechat.feature.chat.resources.setting_chip_connecting
import com.garfiec.librechat.feature.chat.resources.setting_chip_failed
import com.garfiec.librechat.feature.chat.resources.setting_chip_needs_setup
import com.garfiec.librechat.feature.chat.resources.setting_chip_needs_sign_in
import com.garfiec.librechat.feature.chat.resources.setting_chip_not_connected
import com.garfiec.librechat.feature.chat.resources.setting_chip_on_demand
import org.jetbrains.compose.resources.StringResource

/**
 * An MCP server's status dot in the chat (its settings chip, its "+" sheet row), coloured as the MCP
 * servers page colours it: connected green, connecting the info tint, needing sign-in or setup amber,
 * failed the error colour, and the rest neutral.
 */
@Composable
@ReadOnlyComposable
internal fun mcpStatusColor(status: McpDisplayStatus): Color {
    val colors = statusColors()
    return when (status) {
        McpDisplayStatus.CONNECTED -> colors.success
        McpDisplayStatus.CONNECTING -> colors.info
        McpDisplayStatus.NEEDS_SIGN_IN, McpDisplayStatus.NEEDS_SETUP -> colors.warning
        McpDisplayStatus.FAILED -> colors.error
        McpDisplayStatus.ON_DEMAND, McpDisplayStatus.DISCONNECTED, McpDisplayStatus.UNKNOWN -> colors.neutral
    }
}

/** What the dot says, for screen readers. */
internal fun mcpStatusLabel(status: McpDisplayStatus): StringResource = when (status) {
    McpDisplayStatus.CONNECTED -> Res.string.setting_chip_connected
    McpDisplayStatus.CONNECTING -> Res.string.setting_chip_connecting
    McpDisplayStatus.NEEDS_SIGN_IN -> Res.string.setting_chip_needs_sign_in
    McpDisplayStatus.NEEDS_SETUP -> Res.string.setting_chip_needs_setup
    McpDisplayStatus.ON_DEMAND -> Res.string.setting_chip_on_demand
    McpDisplayStatus.FAILED -> Res.string.setting_chip_failed
    McpDisplayStatus.DISCONNECTED, McpDisplayStatus.UNKNOWN -> Res.string.setting_chip_not_connected
}
