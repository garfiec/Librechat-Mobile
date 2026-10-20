package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/** The fill behind a selected or open list row: the glass control fill, or Material's secondary container. */
val adaptiveSelectedRowColor: Color
    @Composable get() = if (isLiquidGlass) GlassControlColors.fill else MaterialTheme.colorScheme.secondaryContainer
