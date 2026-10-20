package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic state colours: something worked, is in progress, needs the user, is idle, or failed.
 * Separate from the accent, and the same in both interface styles. Green and amber come from the
 * web's series palette so they shift with light/dark like the rest of it; the others are scheme
 * roles so they follow the accent and dynamic colour.
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
fun statusColors(): StatusColors = StatusColors(
    success = SeriesColors.slot(SUCCESS_SLOT),
    info = MaterialTheme.colorScheme.primary,
    warning = SeriesColors.slot(WARNING_SLOT),
    neutral = MaterialTheme.colorScheme.outline,
    error = MaterialTheme.colorScheme.error,
)

private const val SUCCESS_SLOT = 7
private const val WARNING_SLOT = 4
