package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.runtime.compositionLocalOf
import com.garfiec.librechat.core.model.preferences.InlineArtifactPrefs

/**
 * Composition-scoped inline-artifact preferences. Provided once at the chat
 * screen root after collecting from [SettingsDataStore]; consumed by
 * [TextContentPart] (deep inside the per-message content renderer) to decide
 * whether to render an artifact inline or as a tap-to-open button.
 *
 * Default value is all-false so any composable outside the provided scope
 * preserves the existing button behavior.
 */
val LocalInlineArtifactPrefs = compositionLocalOf { InlineArtifactPrefs() }
