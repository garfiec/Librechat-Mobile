package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.core.ui.theme.themeShapes

/**
 * The M3 exposed dropdown box, unchanged in both styles: it only wires the field
 * (`Modifier.menuAnchor`) to the menu. Open the menu with [AdaptiveExposedDropdownMenu].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveExposedDropdownMenuBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ExposedDropdownMenuBoxScope.() -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        content = content,
    )
}

/**
 * The menu of an [AdaptiveExposedDropdownMenuBox], still anchored to its field and sized to it.
 * Material: the M3 exposed menu with every parameter forwarded, rounded to the theme's menu corner
 * rather than M3's 4dp. Liquid Glass: the same popup restyled like [AdaptiveDropdownMenu]'s
 * windowed fallback (16dp, opaque `surfaceContainerHigh`, hairline border, shadow); [tonalElevation],
 * [shadowElevation] and [border] apply to Material only. A [shape] or [containerColor] the caller
 * passes wins in both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.AdaptiveExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    matchAnchorWidth: Boolean = true,
    shape: Shape? = null,
    containerColor: Color? = null,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = isLiquidGlass
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        scrollState = scrollState,
        matchAnchorWidth = matchAnchorWidth,
        shape = shape ?: if (glass) AdaptiveMenuShape else themeShapes().menu,
        containerColor = containerColor
            ?: if (glass) MaterialTheme.colorScheme.surfaceContainerHigh else MenuDefaults.containerColor,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = if (glass) GlassExposedMenuShadow else shadowElevation,
        border = if (glass) BorderStroke(GlassDefaults.Hairline, GlassControlColors.separator) else border,
        content = content,
    )
}

private val GlassExposedMenuShadow = 12.dp
