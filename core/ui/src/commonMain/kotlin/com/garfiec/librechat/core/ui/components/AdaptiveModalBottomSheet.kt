package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars
import com.garfiec.librechat.core.ui.glass.GlassFloatingSheet
import com.garfiec.librechat.core.ui.glass.GlassPortal
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.glass.LocalGlassSheetHost
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * In Liquid Glass, a floating glass panel portalled through [GlassSheetHost]. [sheetState] and
 * [shape] apply to the M3 sheet only.
 *
 * Outside a host or inside another window ([LocalInSeparateWindow]) glass falls back to the M3 sheet
 * on an opaque container: translucent without blur shows the content behind sharply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    CoversNativeBars()
    val glass = isLiquidGlass
    val host = LocalGlassSheetHost.current
    if (glass && host != null && !LocalInSeparateWindow.current) {
        // Read through state, so the portal's lambda stays the same and the open sheet recomposes
        // only when one of these changes, not whenever its opener does.
        val currentOnDismiss by rememberUpdatedState(onDismissRequest)
        val currentModifier by rememberUpdatedState(modifier)
        val currentDragHandle by rememberUpdatedState(dragHandle)
        val currentContent by rememberUpdatedState(content)
        GlassPortal(
            host,
            remember { { GlassFloatingSheet({ currentOnDismiss() }, currentModifier, currentDragHandle, currentContent) } },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = if (glass) MaterialTheme.colorScheme.surfaceContainerLow else BottomSheetDefaults.ContainerColor,
        tonalElevation = if (glass) 0.dp else BottomSheetDefaults.Elevation,
        dragHandle = dragHandle,
    ) {
        // Provides a local and emits nothing, so the content still lays out in the sheet's own column.
        CompositionLocalProvider(LocalInSeparateWindow provides true) { content() }
    }
}
