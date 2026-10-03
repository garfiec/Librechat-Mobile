package com.garfiec.librechat.core.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.DRAG_HIGHLIGHT_ALPHA
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.drawHighlight
import com.garfiec.librechat.core.ui.components.rememberMenuDragEffects
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme

/** Opens a glass menu in [host] while [expanded], anchored to this call's parent layout, as an M3 `DropdownMenu` is. */
@Composable
internal fun GlassMenuPortal(
    host: GlassSheetHostState,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    scrollState: ScrollState,
    containerColor: Color?,
    dragSelection: MenuDragSelection?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    CoversNativeBars()
    var anchor by remember { mutableStateOf<Rect?>(null) }
    // A zero-size marker where M3's popup would sit; its parent's bounds are the anchor. Kept in
    // state, so a menu whose anchor moves (a scrolled list) follows it.
    Box(Modifier.size(0.dp).onPlaced { coordinates -> anchor = coordinates.parentLayoutCoordinates?.boundsInRoot() })
    // Read through state, so the portal's lambda stays the same and the open menu recomposes only
    // when one of these changes, not whenever its opener does.
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentModifier by rememberUpdatedState(modifier)
    val currentOffset by rememberUpdatedState(offset)
    val currentContainer by rememberUpdatedState(containerColor)
    val currentDrag by rememberUpdatedState(dragSelection)
    val currentContent by rememberUpdatedState(content)
    GlassPortal(
        host,
        remember(scrollState) {
            {
                val bounds = anchor
                if (bounds != null) {
                    GlassFloatingMenu(
                        anchor = bounds,
                        onDismissRequest = { currentOnDismiss() },
                        modifier = currentModifier,
                        offset = currentOffset,
                        scrollState = scrollState,
                        containerColor = currentContainer,
                        drag = currentDrag,
                        content = currentContent,
                    )
                }
            }
        },
    )
}

/**
 * Below [anchor] (above when there's no room), aligned to its nearer edge. The dismissing touch is
 * consumed, as in M3. With [drag], a press-drag-release from the opener highlights rows under the
 * finger, and the panel leans toward it and stretches past its far edge, as the Material menu does.
 */
// [modifier] is the caller's, for the menu panel (a width, a max height), as on an M3 menu; the
// root here is the full-screen dismiss layer, which is not what it is meant for.
@Suppress("ModifierNotUsedAtRoot")
@Composable
private fun GlassFloatingMenu(
    anchor: Rect,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    scrollState: ScrollState,
    containerColor: Color?,
    drag: MenuDragSelection?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = rememberGlassStyle() ?: return
    val backdrop = LocalGlassBackdrop.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    PlatformBackHandler(enabled = true) { dismiss() }

    val shown = remember { Animatable(0f) }
    LaunchedEffect(shown) { shown.animateTo(1f, ShowSpec) }
    // Where this overlay sits in the root, to turn the anchor's root bounds into local ones.
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var growFrom by remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val effects = rememberMenuDragEffects(drag, opening = true, opensDown = { growFrom.pivotFractionY == 0f })
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DRAG_HIGHLIGHT_ALPHA)
    val fill = containerColor ?: GlassDefaults.panelThickening(MaterialTheme.colorScheme.surface, LocalDarkTheme.current)
    val safeArea = WindowInsets.safeDrawing

    Box(Modifier.fillMaxSize().onPlaced { hostOrigin = it.positionInRoot() }) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (waitForUpOrCancellation() != null) dismiss()
                    }
                },
        )
        Layout(
            content = {
                Column(
                    modifier = modifier
                        .width(IntrinsicSize.Max)
                        .onPlaced { effects.frame = it }
                        .graphicsLayer {
                            // Stretches from the edge nearest the anchor toward the finger.
                            val squeeze = 1f - CANCEL_SHRINK * effects.shrink.value
                            scaleX = squeeze
                            scaleY = squeeze * (1f + effects.stretch.value / size.height.coerceAtLeast(1f))
                            transformOrigin = TransformOrigin(0.5f, growFrom.pivotFractionY)
                            translationX = effects.lean.value.x
                            translationY = effects.lean.value.y
                        }
                        .graphicsLayer {
                            alpha = shown.value
                            val scale = START_SCALE + (1f - START_SCALE) * shown.value
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = growFrom
                        }
                        .glassSurface(style, GlassDefaults.MenuShape, backdrop)
                        .clip(GlassDefaults.MenuShape)
                        .background(fill)
                        .onPlaced { effects.inside = it }
                        .drawBehind { if (drag != null) drawHighlight(drag.highlight, effects.inside, highlightColor) }
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp)
                        // The content behind is still in the tree, unlike behind a popup window.
                        .semantics { isTraversalGroup = true },
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        content()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = MenuMargin.roundToPx()
            val top = safeArea.getTop(this) + margin
            val bottom = constraints.maxHeight - safeArea.getBottom(this) - margin
            val placeable = measurables.first().measure(
                Constraints(
                    minWidth = MenuMinWidth.roundToPx(),
                    maxWidth = minOf(MenuMaxWidth.roundToPx(), constraints.maxWidth - 2 * margin),
                    maxHeight = (bottom - top).coerceAtLeast(0),
                ),
            )
            val local = anchor.translate(-hostOrigin)
            val alignEnd = local.center.x > constraints.maxWidth / 2f
            val preferredX = if (alignEnd) local.right - placeable.width else local.left
            val x = (preferredX + offset.x.roundToPx()).toInt()
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val below = local.bottom.toInt() + offset.y.roundToPx()
            val above = local.top.toInt() - offset.y.roundToPx() - placeable.height
            val opensDown = below + placeable.height <= bottom || above < top
            val y = (if (opensDown) below else above).coerceIn(top, (bottom - placeable.height).coerceAtLeast(top))
            growFrom = TransformOrigin(if (alignEnd) 1f else 0f, if (opensDown) 0f else 1f)
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
        }
    }
}

private const val START_SCALE = 0.85f
private const val CANCEL_SHRINK = 0.03f
private val MenuMargin = 8.dp
private val MenuMinWidth = 112.dp
private val MenuMaxWidth = 280.dp
private val ShowSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)
