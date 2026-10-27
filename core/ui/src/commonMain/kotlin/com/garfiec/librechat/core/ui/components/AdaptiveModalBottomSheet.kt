package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * In Liquid Glass, a floating glass panel portalled through [GlassSheetHost]. [sheetState] and
 * [shape] apply to the M3 sheet only.
 *
 * While [secure] is true the sheet stays out of screenshots, screen recording and the recents
 * snapshot, which is written to disk: set it while a secret is revealed. On Android the M3 sheet's
 * window is made secure, and the glass sheet, which draws in the activity window, makes that window
 * secure (see [SecureWindowEffect]). The window stays secure for a couple of frames after [secure]
 * turns off, so the frame that hides the secret is on screen before it stops being secure. iOS has
 * no equivalent; re-mask on ON_PAUSE there (ON_STOP is too late for the app-switcher snapshot).
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
    secure: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = AdaptiveModalBottomSheetImpl(
    onDismissRequest = onDismissRequest,
    interceptDismiss = false,
    modifier = modifier,
    sheetState = sheetState,
    shape = shape,
    dragHandle = dragHandle,
    secure = secure,
    content = content,
)

/**
 * A sheet that can hold itself open, e.g. over an editor with unsaved changes. While
 * [interceptDismiss] is true, a swipe or fling down, a scrim tap, a drag-handle tap and back keep
 * the sheet up and call [onDismissRequest] instead, so the caller can ask first; the sheet closes
 * when the caller removes it from composition. While false it behaves like the overload above, and
 * [onDismissRequest] runs once the sheet has gone; if [interceptDismiss] turned true while it was
 * going, the sheet comes back up as it asks.
 *
 * Owns its [SheetState], since in Material the hold lives in the state's `confirmValueChange`.
 * [secure] is as on the overload above.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    interceptDismiss: Boolean,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    secure: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentIntercept by rememberUpdatedState(interceptDismiss)
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    // Remembered: the state's rememberSaveable is keyed on this lambda, so a fresh one per
    // recomposition would recreate the state and close the sheet.
    val confirmValueChange = remember {
        { target: SheetValue ->
            if (target == SheetValue.Hidden && currentIntercept) {
                currentOnDismiss()
                false
            } else {
                true
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = confirmValueChange,
    )
    val scope = rememberCoroutineScope()
    AdaptiveModalBottomSheetImpl(
        onDismissRequest = onDismissRequest,
        // M3 reports here once the sheet has hidden. If the caller started holding during that hide
        // (a keystroke dirtied a draft, a save went out), it will refuse to close, so bring the
        // sheet back rather than leave it composed and hidden.
        onMaterialHide = {
            if (currentIntercept && !sheetState.isVisible) scope.launch { sheetState.show() }
            currentOnDismiss()
        },
        interceptDismiss = interceptDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        dragHandle = dragHandle,
        secure = secure,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveModalBottomSheetImpl(
    onDismissRequest: () -> Unit,
    interceptDismiss: Boolean,
    sheetState: SheetState,
    shape: Shape,
    dragHandle: @Composable (() -> Unit)?,
    secure: Boolean,
    modifier: Modifier = Modifier,
    /** What the M3 sheet calls once it has hidden; the glass sheet handles that case itself. */
    onMaterialHide: () -> Unit = onDismissRequest,
    content: @Composable ColumnScope.() -> Unit,
) {
    CoversNativeBars()
    val windowSecure = rememberLingeringSecure(secure)
    val glass = isLiquidGlass
    val host = LocalGlassSheetHost.current
    if (glass && host != null && !LocalInSeparateWindow.current) {
        // Read through state, so the portal's lambda stays the same and the open sheet recomposes
        // only when one of these changes, not whenever its opener does.
        val currentOnDismiss by rememberUpdatedState(onDismissRequest)
        val currentIntercept by rememberUpdatedState(interceptDismiss)
        val currentModifier by rememberUpdatedState(modifier)
        val currentDragHandle by rememberUpdatedState(dragHandle)
        val currentContent by rememberUpdatedState(content)
        // The glass sheet draws in the activity window, so that window is what must turn secure.
        SecureWindowEffect(windowSecure)
        GlassPortal(
            host,
            remember {
                {
                    GlassFloatingSheet(
                        onDismissRequest = { currentOnDismiss() },
                        interceptDismiss = { currentIntercept },
                        modifier = currentModifier,
                        dragHandle = currentDragHandle,
                        content = currentContent,
                    )
                }
            },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onMaterialHide,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = if (glass) MaterialTheme.colorScheme.surfaceContainerLow else BottomSheetDefaults.ContainerColor,
        tonalElevation = if (glass) 0.dp else BottomSheetDefaults.Elevation,
        dragHandle = dragHandle,
        properties = secureSheetProperties(windowSecure),
    ) {
        // Provides a local and emits nothing, so the content still lays out in the sheet's own column.
        CompositionLocalProvider(LocalInSeparateWindow provides true) {
            // The sheet is its own window here, so only a handler inside it sees back. Taking back
            // before Material3's own handler also skips its predictive-back shrink, which a vetoed
            // hide would otherwise leave on screen.
            PlatformBackHandler(enabled = interceptDismiss, onBack = onDismissRequest)
            content()
        }
    }
}
