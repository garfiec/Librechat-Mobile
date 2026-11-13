package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveSideRail
import com.garfiec.librechat.core.ui.components.topbar.LocalRailParent
import com.garfiec.librechat.core.ui.components.topbar.LocalRailSlot
import com.garfiec.librechat.core.ui.components.topbar.RailSlot
import com.garfiec.librechat.core.ui.components.topbar.SlotRail
import com.garfiec.librechat.core.ui.components.topbar.railTraversalScope
import com.garfiec.librechat.core.ui.components.topbar.rememberRailPlacement
import com.garfiec.librechat.core.ui.glass.GlassBackdropRecorder
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.rememberGlassBackdrop

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
    val parent = LocalRailParent.current
    val slot = remember(parent) { RailSlot(parent) }
    val placement = rememberRailPlacement()
    val railMode = placement.bars != null
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        // The same tree with and without bars, so a fold or rotation that brings them keeps the screen's state.
        Box(modifier = modifier.then(placement.modifier).then(railTraversalScope(railMode))) {
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
            SlotRail(slot, placement.bars)
        }
    }
}
