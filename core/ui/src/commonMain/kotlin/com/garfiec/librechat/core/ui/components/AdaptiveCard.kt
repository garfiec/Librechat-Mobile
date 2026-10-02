package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme

/**
 * In Liquid Glass, a glass panel over [LocalGlassBackdrop], for a card floating over content.
 * [colors] apply in Material only; [shape] in both when it is corner-based.
 */
@Composable
fun AdaptiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = rememberGlassStyle()
    if (style == null) {
        Card(modifier = modifier, shape = shape, colors = colors, content = content)
        return
    }
    val glassShape = shape as? CornerBasedShape ?: GlassDefaults.MenuShape
    Column(
        modifier
            .glassSurface(style, glassShape, LocalGlassBackdrop.current)
            .clip(glassShape)
            .background(GlassDefaults.panelThickening(MaterialTheme.colorScheme.surface, LocalDarkTheme.current)),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}
