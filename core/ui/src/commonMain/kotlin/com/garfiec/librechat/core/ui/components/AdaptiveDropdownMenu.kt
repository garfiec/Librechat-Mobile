package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.glass.GlassMenuPortal
import com.garfiec.librechat.core.ui.glass.LocalGlassSheetHost
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * In Material, a menu that springs out of its corner nearest the anchor, in the app canvas
 * ([MaterialMenuOverlay]), or, inside a dialog or sheet window, a popup that unfolds from it
 * ([MaterialMenuPopup]). In Liquid Glass, a glass panel portalled into the app canvas; [properties]
 * apply to the popup only. Outside a host or inside another window it falls back to a rounded opaque
 * popup. [dragSelection] wires the menu to an opener's [menuDragAnchor] for press-drag-release
 * (not in the fallback popups' glass look).
 */
// One branch runs per call; the rule counts the Material overlay, popup and M3 menu as if all did.
@Suppress("MultipleEmitters")
@Composable
fun AdaptiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape? = null,
    containerColor: Color? = null,
    dragSelection: MenuDragSelection? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = isLiquidGlass
    val host = LocalGlassSheetHost.current
    if (glass && host != null && !LocalInSeparateWindow.current) {
        GlassMenuPortal(host, expanded, onDismissRequest, offset, scrollState, containerColor, dragSelection, modifier, content)
        return
    }
    if (!glass && host != null && !LocalInSeparateWindow.current) {
        MaterialMenuOverlay(
            host = host,
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            scrollState = scrollState,
            shape = shape ?: MenuDefaults.shape,
            containerColor = containerColor ?: MenuDefaults.containerColor,
            dragSelection = dragSelection,
            content = content,
        )
        return
    }
    if (!glass) {
        MaterialMenuPopup(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            shape = shape ?: MenuDefaults.shape,
            containerColor = containerColor ?: MenuDefaults.containerColor,
            dragSelection = dragSelection,
            content = content,
        )
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape ?: GlassDefaults.MenuShape,
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = GlassMenuShadow,
        border = BorderStroke(GlassDefaults.Hairline, GlassControlColors.separator),
        content = content,
    )
}

private val GlassMenuShadow = 12.dp
