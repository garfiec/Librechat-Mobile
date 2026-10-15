package com.garfiec.librechat.core.ui.theme

import androidx.compose.ui.graphics.Color

// Light: bright marker colours. Dark: muted, so light text stays readable on them.
private val MatchLight = Color(0xFFFFEB3B)
private val FocusedMatchLight = Color(0xFFFF9800)
private val MatchDark = Color(0xFF5C4800)
private val FocusedMatchDark = Color(0xFF804D00)

/**
 * The background behind a search match, shared by find-in-chat and the Search screen so a match
 * looks the same wherever it's shown. Pass [isSurfaceDark], not the system setting, so an in-app
 * theme choice is honoured.
 */
fun searchMatchColor(isDark: Boolean): Color = if (isDark) MatchDark else MatchLight

/** The background behind the current match while stepping through matches. */
fun searchFocusedMatchColor(isDark: Boolean): Color = if (isDark) FocusedMatchDark else FocusedMatchLight
