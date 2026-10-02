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
 * In Liquid Glass, a glass panel portalled into the app canvas; [properties] apply to the M3 popup
 * only. Outside a host or inside another window it falls back to a rounded opaque popup.
 */
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = isLiquidGlass
    val host = LocalGlassSheetHost.current
    if (glass && host != null && !LocalInSeparateWindow.current) {
        GlassMenuPortal(host, expanded, onDismissRequest, offset, scrollState, containerColor, modifier, content)
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape ?: if (glass) GlassDefaults.MenuShape else MenuDefaults.shape,
        containerColor = containerColor ?: if (glass) MaterialTheme.colorScheme.surfaceContainerHigh else MenuDefaults.containerColor,
        tonalElevation = if (glass) 0.dp else MenuDefaults.TonalElevation,
        shadowElevation = if (glass) GlassMenuShadow else MenuDefaults.ShadowElevation,
        border = if (glass) BorderStroke(GlassDefaults.Hairline, GlassControlColors.separator) else null,
        content = content,
    )
}

private val GlassMenuShadow = 12.dp
