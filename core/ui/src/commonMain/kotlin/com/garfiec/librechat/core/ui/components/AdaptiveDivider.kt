package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * An M3 [HorizontalDivider] in Material. In Liquid Glass it is an iOS hairline: thinner, in the
 * separator colour, inset from the leading edge inside a grouped cell, and left out after the last
 * cell of a group (whose rounded edge already ends it). A [thickness] or [color] the caller sets wins
 * in either style.
 */
@Composable
fun AdaptiveDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified,
) {
    if (!isLiquidGlass) {
        HorizontalDivider(
            modifier = modifier,
            thickness = if (thickness.isSpecified) thickness else DividerDefaults.Thickness,
            color = if (color.isSpecified) color else DividerDefaults.color,
        )
        return
    }
    val position = LocalGroupPosition.current
    when {
        // Keep the caller's spacing (a divider often carries a top padding) but draw nothing.
        position?.isLast == true -> Spacer(modifier)
        else -> HorizontalDivider(
            modifier = modifier.padding(start = if (position != null) SeparatorInset else 0.dp),
            thickness = if (thickness.isSpecified) thickness else GlassDefaults.Hairline,
            color = if (color.isSpecified) color else GlassControlColors.separator,
        )
    }
}

private val SeparatorInset = 16.dp
