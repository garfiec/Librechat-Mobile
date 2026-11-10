package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveSideRail
import com.garfiec.librechat.core.ui.components.topbar.LocalRailParent
import com.garfiec.librechat.core.ui.components.topbar.LocalRailSlot
import com.garfiec.librechat.core.ui.components.topbar.RailSlot
import com.garfiec.librechat.core.ui.components.topbar.reachesBarSide
import com.garfiec.librechat.core.ui.glass.GlassBackdropRecorder
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.rememberGlassBackdrop
import com.garfiec.librechat.core.ui.platform.BarSide
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.core.ui.platform.VerticalBars

/**
 * An M3 [Scaffold] whose bar, FAB and snackbar sample the body as their backdrop. To scroll under a
 * glass bar, pass [content]'s padding to the list as `contentPadding` rather than padding the list.
 *
 * While the window has vertical bars and this scaffold reaches the bar side, an `AdaptiveTopBar` in
 * [topBar] draws its controls as an [AdaptiveSideRail] in the bar column, and only its title as a
 * heading. The body already keeps out of the column through [contentWindowInsets]' horizontal sides.
 */
@Composable
fun AdaptiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val bars = LocalWindowFeatures.current.verticalBars
    val parent = LocalRailParent.current
    val slot = remember(parent) { RailSlot(parent) }
    // Unknown until placed; assumed, so a full-width screen doesn't open with a horizontal bar first.
    var atBarSide by remember { mutableStateOf(true) }
    val currentBars by rememberUpdatedState(bars)
    val railMode = bars != null && atBarSide && !LocalInSeparateWindow.current
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        // The same tree with and without bars, so a fold or rotation that brings them keeps the screen's state.
        Box(
            modifier = if (bars != null) {
                modifier.onPlaced { coordinates ->
                    val side = currentBars?.side ?: return@onPlaced
                    val windowWidth = coordinates.findRootCoordinates().size.width.toFloat()
                    atBarSide = reachesBarSide(side, coordinates.positionInRoot().x, coordinates.size.width.toFloat(), windowWidth)
                }
            } else {
                modifier
            },
        ) {
            Scaffold(
                topBar = { CompositionLocalProvider(LocalRailSlot provides slot.takeIf { railMode }, content = topBar) },
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
                floatingActionButton = floatingActionButton,
                floatingActionButtonPosition = floatingActionButtonPosition,
                containerColor = containerColor,
                contentColor = contentColor,
                contentWindowInsets = contentWindowInsets,
            ) { padding ->
                GlassBackdropRecorder(backdrop) {
                    // The body is what the chrome samples, so glass inside it must not sample it too: that
                    // would feed its own output back in. Chrome in the body draws its flat fallback.
                    CompositionLocalProvider(LocalGlassBackdrop provides null, LocalRailParent provides slot) {
                        content(padding)
                    }
                }
            }
            if (bars != null) ScaffoldRail(slot, bars, railMode)
        }
    }
}

/** Draws the rail for the bar in [slot], unless a scaffold nested in this one's body draws its own. */
@Composable
private fun BoxScope.ScaffoldRail(slot: RailSlot, bars: VerticalBars, railMode: Boolean) {
    val spec = slot.spec
    val drawing = railMode && spec != null && !slot.superseded
    val parent = slot.parent
    if (parent != null) {
        DisposableEffect(parent, drawing) {
            if (drawing) parent.addChildClaim()
            onDispose { if (drawing) parent.removeChildClaim() }
        }
    }
    if (drawing && spec != null) {
        AdaptiveSideRail(
            spec = spec,
            bars = bars,
            modifier = Modifier.align(if (bars.side == BarSide.RIGHT) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft),
        )
    }
}
