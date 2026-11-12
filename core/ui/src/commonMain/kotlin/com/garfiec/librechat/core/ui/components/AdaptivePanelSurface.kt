package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.glass.SheetMargin
import com.garfiec.librechat.core.ui.glass.SheetShape
import com.garfiec.librechat.core.ui.glass.glassSheetPanel
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme

/** Where an [AdaptivePanelSurface] stands in the screen that places it. */
enum class PanelPlacement {
    /** Rising from the bottom edge. */
    BOTTOM_SHEET,

    /** Along the end edge, beside the screen's own content. */
    SIDE,

    /** Over all of the screen. */
    WHOLE,
}

/**
 * The surface of a panel a screen places and moves itself, padded inside for the system bars it
 * reaches. Its composition is the same in every [placement], so what it holds keeps its state (a
 * web view keeps its page) when the placement changes. In Liquid Glass it floats over [backdrop],
 * which records what the panel covers and never the panel itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePanelSurface(
    placement: PanelPlacement,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = rememberGlassStyle()
    val outer: Modifier
    val panel: Modifier
    val inner: Modifier
    if (style == null) {
        val shape: Shape = if (placement == PanelPlacement.BOTTOM_SHEET) BottomSheetDefaults.ExpandedShape else RectangleShape
        val color = when (placement) {
            PanelPlacement.BOTTOM_SHEET -> BottomSheetDefaults.ContainerColor
            PanelPlacement.SIDE -> MaterialTheme.colorScheme.surfaceContainerLow
            PanelPlacement.WHOLE -> MaterialTheme.colorScheme.surface
        }
        outer = modifier.clip(shape)
        panel = Modifier.background(color)
        inner = Modifier.windowInsetsPadding(barsReached(placement))
    } else {
        val thickening = GlassDefaults.panelThickening(MaterialTheme.colorScheme.surface, LocalDarkTheme.current)
        val shape = if (placement == PanelPlacement.WHOLE) SquareShape else SheetShape
        outer = when (placement) {
            PanelPlacement.BOTTOM_SHEET -> modifier.windowInsetsPadding(barsReached(placement)).padding(SheetMargin)
            PanelPlacement.SIDE -> modifier.windowInsetsPadding(barsReached(placement))
                .padding(top = SheetMargin, bottom = SheetMargin, end = SheetMargin)
            PanelPlacement.WHOLE -> modifier
        }
        // Over the whole screen there is nothing beside it to see through to: opaque, like the screen.
        panel = if (placement == PanelPlacement.WHOLE) {
            Modifier.background(MaterialTheme.colorScheme.surface)
        } else {
            Modifier.glassSheetPanel(style, backdrop, thickening, shape)
        }
        inner = Modifier.clip(shape).then(
            if (placement == PanelPlacement.WHOLE) Modifier.windowInsetsPadding(barsReached(placement)) else Modifier,
        )
    }
    Box(outer) {
        // The panel as a sibling behind the content, not a modifier around it: inside a glass layer a
        // UIKitView's see-through cut-out (an artifact's WKWebView) stops there and never shows.
        Box(Modifier.matchParentSize().then(panel))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(inner) { content() }
        }
    }
}

/** The system bars (and keyboard) a panel in [placement] reaches, which its content keeps clear of. */
@Composable
private fun barsReached(placement: PanelPlacement): WindowInsets = when (placement) {
    // The sheet's top stops below the status bar; its placer sees to that.
    PanelPlacement.BOTTOM_SHEET -> WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    PanelPlacement.SIDE -> WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
    PanelPlacement.WHOLE -> WindowInsets.safeDrawing
}

private val SquareShape = RoundedCornerShape(0.dp)
