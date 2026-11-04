package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * A status dot for a glyph's corner (the drawer's unread dot, a pull request's CI dot), ringed in
 * [ringColor] so it reads on any glyph. The ring defaults to the drawer's surface in Material and is
 * left out in glass, where the surface behind is the backdrop. Decorative: the caller says in words
 * what it means.
 */
@Composable
fun CornerStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    ringColor: Color = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(ringColor, CircleShape)
            .padding(size / RING_FRACTION)
            .background(color, CircleShape),
    )
}

/** The ring is a fifth of the dot's size each side (2dp on the 10dp dot). */
private const val RING_FRACTION = 5
