package com.garfiec.librechat.core.model.preferences

import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition

/**
 * The active server's theme (v0.8.9-rc1 `interface.theme`), as last read from its config.
 * [enabled] is the user's choice of the server's colours over Switchboard's for that server; on
 * unless they picked Switchboard's.
 */
data class ServerTheme(
    val serverId: String,
    val definition: ServerThemeDefinition,
    val enabled: Boolean,
) {
    /** The theme to paint with, or null when the user picked Switchboard's colours. */
    val painted: ServerThemeDefinition? get() = definition.takeIf { enabled }
}
