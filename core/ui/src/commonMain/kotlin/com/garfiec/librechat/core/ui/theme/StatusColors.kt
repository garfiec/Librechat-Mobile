package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic state colours: something worked, is in progress, needs the user, is idle, or failed.
 * Separate from the accent, and the same in both interface styles. The active theme fills them
 * ([ThemeTokens.status]).
 */
@Immutable
data class StatusColors(
    val success: Color,
    val info: Color,
    val warning: Color,
    val neutral: Color,
    val error: Color,
)

@Composable
@ReadOnlyComposable
fun statusColors(): StatusColors = LocalThemeTokens.current.status
