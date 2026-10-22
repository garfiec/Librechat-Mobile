package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the Memories page (owned by settings) from a memory card deep in the message list.
 * Provided by [ChatRoot] from the navigation host, which owns the route, so this module never
 * depends on the settings feature.
 *
 * Null when the role may not read memories (MEMORIES USE + READ) or no host is in scope (previews,
 * tests); the card then draws no Manage action.
 */
val LocalOpenMemories = staticCompositionLocalOf<(() -> Unit)?> { null }
