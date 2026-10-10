package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.garfiec.librechat.core.ui.glass.GlassPortal
import com.garfiec.librechat.core.ui.glass.GlassSheetHostState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Material dropdown menu drawn in the app's own canvas (portalled into [host], as the Liquid
 * Glass menu is), so it can grow past its final size: the panel starts small in its own corner
 * nearest the anchor — inside its final bounds, never over the anchor, so a second tap on the opener
 * can't land on it — and its width and height spring out on separate, underdamped springs (width
 * leading, height following), overshooting a little before settling. Rows come into focus once the
 * panel is under way, the app dims behind it, and the shadow grows with it. Closing reverses into
 * that corner, or into the row a press-drag-release picked. Placed exactly where M3's
 * `DropdownMenu` places it.
 *
 * Inside a dialog or sheet window ([LocalInSeparateWindow]) the canvas lies beneath the window, so
 * [MaterialMenuPopup] draws it there instead.
 */
@Composable
internal fun MaterialMenuOverlay(
    host: GlassSheetHostState,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    scrollState: ScrollState,
    shape: Shape,
    containerColor: Color,
    dragSelection: MenuDragSelection?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return

    var anchor by remember { mutableStateOf<Rect?>(null) }
    // A zero-size marker where M3's popup would sit; its parent's bounds are the anchor, as for M3.
    Box(Modifier.size(0.dp).onPlaced { coordinates -> anchor = coordinates.parentLayoutCoordinates?.boundsInRoot() })
    // Through state, so the portal's lambda stays the same and the open menu recomposes only when one
    // of these changes, not whenever its opener does (the chat bar recomposes on every stream chunk).
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentModifier by rememberUpdatedState(modifier)
    val currentOffset by rememberUpdatedState(offset)
    val currentShape by rememberUpdatedState(shape)
    val currentContainer by rememberUpdatedState(containerColor)
    val currentDrag by rememberUpdatedState(dragSelection)
    val currentContent by rememberUpdatedState(content)
    GlassPortal(
        host,
        remember(expandedState, scrollState) {
            {
                val bounds = anchor
                if (bounds != null) {
                    MorphingMenu(
                        anchor = bounds,
                        expandedState = expandedState,
                        onDismissRequest = { currentOnDismiss() },
                        modifier = currentModifier,
                        offset = currentOffset,
                        scrollState = scrollState,
                        shape = currentShape,
                        containerColor = currentContainer,
                        drag = currentDrag,
                        content = currentContent,
                    )
                }
            }
        },
    )
}

// PredictiveBackHandler is deprecated in favour of NavigationEventHandler, which only lands in a
// later Compose; ZoomableMediaPager stays on it for the same reason.
@Suppress("DEPRECATION", "ModifierNotUsedAtRoot", "LongMethod")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MorphingMenu(
    anchor: Rect,
    expandedState: MutableTransitionState<Boolean>,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    scrollState: ScrollState,
    shape: Shape,
    containerColor: Color,
    drag: MenuDragSelection?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val open = expandedState.targetState

    val transition = rememberTransition(expandedState, label = "MaterialMenuMorph")
    val width = transition.animateFloat(
        transitionSpec = { if (targetState) WidthOpenSpec else WidthCloseSpec },
        label = "width",
    ) { if (it) 1f else 0f }
    val height = transition.animateFloat(
        transitionSpec = { if (targetState) HeightOpenSpec else HeightCloseSpec },
        label = "height",
    ) { if (it) 1f else 0f }
    val rowsClock = transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(RowsTotalMillis, delayMillis = ROWS_DELAY_MILLIS, easing = LinearEasing) else snap()
        },
        label = "rows",
    ) { if (it) 1f else 0f }

    // A back gesture folds the menu part way toward its anchor as it moves; completing it closes the
    // menu from there, abandoning it springs the menu back open. The dismiss layer below ignores a
    // touch the system cancels, so the edge swipe that starts the gesture doesn't close the menu.
    val back = remember { Animatable(0f) }
    LaunchedEffect(open) { if (open) back.snapTo(0f) }
    PredictiveBackHandler(enabled = open) { progress ->
        try {
            progress.collect { event -> back.snapTo(event.progress) }
            dismiss()
        } catch (cancellation: CancellationException) {
            back.animateTo(0f, BackCancelSpec)
            throw cancellation
        }
    }

    // The morph's two axes, 0 = the grow corner (or a picked row), 1 = the menu; past 1 while overshooting.
    val axes = remember { Axes(width, height, back) }

    // Where this overlay and the panel sit, to turn root coordinates into the panel's own.
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var panelPosition by remember { mutableStateOf(IntOffset.Zero) }
    var opensDown by remember { mutableStateOf(true) }
    val effects = rememberMenuDragEffects(drag, opening = open, opensDown = { opensDown })
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DRAG_HIGHLIGHT_ALPHA)
    // The canvas runs edge to edge, under the keyboard; M3's popup only sees the visible frame.
    val ime = WindowInsets.ime
    val rows = remember {
        object : State<(Int) -> Float> {
            override val value: (Int) -> Float = { index ->
                if (expandedState.targetState) {
                    // A back gesture fades rows by the same rule the close uses, so a committed
                    // gesture hands over to the close without the rows jumping.
                    rowProgress(rowsClock.value, index) * closeFade(axes.fold())
                } else {
                    // Closing: every row fades together as the panel shrinks past the middle.
                    closeFade(axes.settled())
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().onPlaced { hostOrigin = it.positionInRoot() }) {
        // Dims the app with the morph; while open it is also the outside-tap target, which dismisses
        // on release and ignores a cancelled touch. The dismissing touch is consumed, as in M3.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = SCRIM_ALPHA * axes.settled())) }
                .then(
                    if (open) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                if (waitForUpOrCancellation() != null) dismiss()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.contentColorFor(containerColor)) {
            Layout(
                content = {
                    Layout(
                        content = { MenuColumnScope.content() },
                        modifier = Modifier
                            .onPlaced { effects.frame = it }
                            .graphicsLayer {
                                val px = axes.width()
                                val py = axes.height()
                                val chosen = drag?.selected
                                val pickedRow = if (chosen != null && !expandedState.targetState) effects.frame?.toLocal(chosen) else null
                                val home = pickedRow ?: growCorner(anchor.translate(-hostOrigin).translate(-panelPosition.toOffset()), size)
                                var rect = Rect(
                                    left = lerp(home.left, 0f, px.coerceAtLeast(0f)),
                                    top = lerp(home.top, 0f, py.coerceAtLeast(0f)),
                                    right = lerp(home.right, size.width, px.coerceAtLeast(0f)),
                                    bottom = lerp(home.bottom, size.height, py.coerceAtLeast(0f)),
                                )
                                // Squash and stretch: exaggerate the lead the width spring has over the height.
                                val squash = (SQUASH * (px - py)).coerceIn(-MAX_SQUASH, MAX_SQUASH)
                                rect = rect.scaleAroundCenter(1f + squash, 1f - squash)
                                val stretch = effects.stretch.value
                                rect = if (opensDown) rect.copy(bottom = rect.bottom + stretch) else rect.copy(top = rect.top - stretch)
                                val squeeze = 1f - CANCEL_SHRINK * effects.shrink.value
                                rect = rect.scaleAroundCenter(squeeze, squeeze)

                                val sx = (rect.width / size.width).coerceAtLeast(MIN_SCALE)
                                val sy = (rect.height / size.height).coerceAtLeast(MIN_SCALE)
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = sx
                                scaleY = sy
                                translationX = rect.left + effects.lean.value.x
                                translationY = rect.top + effects.lean.value.y

                                val formed = min(px, py).coerceIn(0f, 1f)
                                val endRadius = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f
                                // Folding into a picked row rounds toward a pill; growing keeps the menu's corners.
                                val startRadius = if (pickedRow != null) pickedRow.minDimension / 2f else endRadius
                                val radius = lerp(startRadius, endRadius, formed)
                                    .coerceAtMost(min(rect.width, rect.height) / 2f)
                                this.shape = if (px == 1f && py == 1f && sx == 1f && sy == 1f) {
                                    shape
                                } else {
                                    ScaledRoundRectShape(radius / sx, radius / sy)
                                }
                                clip = true
                                shadowElevation = lerp(0f, MorphElevation.toPx(), formed)
                                alpha = (max(px, py) / PANEL_FADE_IN).coerceIn(0f, 1f) * (1f - BACK_FADE * back.value)
                            }
                            .background(containerColor)
                            .onPlaced { effects.inside = it }
                            .drawBehind { if (drag != null) drawHighlight(drag.highlight, effects.inside, highlightColor) }
                            // While closing, rows are on their way out: swallow taps so one can't fire twice.
                            .then(if (open) Modifier else Modifier.pointerInput(Unit) { consumeAll() })
                            .then(modifier)
                            .padding(vertical = MenuVerticalPadding)
                            .width(IntrinsicSize.Max)
                            .verticalScroll(scrollState)
                            .semantics { isTraversalGroup = true },
                        measurePolicy = remember(density) {
                            StaggeredColumnPolicy(
                                rowProgress = { index -> rows.value(index) },
                                rowShiftPx = with(density) { RowShift.toPx() },
                                opensDown = { opensDown },
                            )
                        },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val visibleHeight = (constraints.maxHeight - ime.getBottom(this)).coerceAtLeast(0)
                val placeable = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = visibleHeight))
                val anchorInHost = anchor.translate(-hostOrigin).let {
                    IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
                }
                val position = MaterialMenuPositionProvider(offset, density) { _, _ -> }.calculatePosition(
                    anchorBounds = anchorInHost,
                    windowSize = IntSize(constraints.maxWidth, visibleHeight),
                    layoutDirection = layoutDirection,
                    popupContentSize = IntSize(placeable.width, placeable.height),
                )
                panelPosition = position
                opensDown = position.y >= anchorInHost.top
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(position) }
            }
        }
    }
}

/** The morph's progress per axis, folded by an in-progress back gesture. */
private class Axes(
    private val width: State<Float>,
    private val height: State<Float>,
    private val back: Animatable<Float, *>,
) {
    fun fold() = 1f - BACK_FOLD * back.value
    fun width() = width.value * fold()
    fun height() = height.value * fold()

    /** How far the panel has formed, 0..1. */
    fun settled() = min(width(), height()).coerceIn(0f, 1f)
}

internal suspend fun PointerInputScope.consumeAll() {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}

/** Rows' opacity while the panel shrinks: full until it is [CLOSE_FADE_START] formed, then fading. */
private fun closeFade(formed: Float) = ((formed - CLOSE_FADE_START) / (1f - CLOSE_FADE_START)).coerceIn(0f, 1f)

/**
 * Where the panel grows from: [GROW_FROM_SCALE] of its size, in its own corner nearest [anchor]
 * (both in the panel's coordinates) — inside the panel, so never over the anchor.
 */
internal fun growCorner(anchor: Rect, panel: Size): Rect {
    val w = panel.width * GROW_FROM_SCALE
    val h = panel.height * GROW_FROM_SCALE
    val left = if (anchor.center.x >= panel.width / 2f) panel.width - w else 0f
    val top = if (anchor.center.y <= panel.height / 2f) 0f else panel.height - h
    return Rect(left, top, left + w, top + h)
}

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

private fun Rect.scaleAroundCenter(sx: Float, sy: Float): Rect {
    val c = center
    val halfW = width * sx / 2f
    val halfH = height * sy / 2f
    return Rect(c.x - halfW, c.y - halfH, c.x + halfW, c.y + halfH)
}

/**
 * A rounded rect over the whole layer, with radii pre-divided by the layer's scale so the corners
 * render round after scaling — a disc at the start of the morph, not a lozenge.
 */
private class ScaledRoundRectShape(private val radiusX: Float, private val radiusY: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                cornerRadius = CornerRadius(radiusX.coerceAtMost(size.width / 2f), radiusY.coerceAtMost(size.height / 2f)),
            ),
        )
}

/** Width leads, height follows; both overshoot a little, height more. */
private val WidthOpenSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 340f)
private val HeightOpenSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 225f)

/** A softer settle on the way back in. */
private val WidthCloseSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
private val HeightCloseSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)

private val MorphElevation = 6.dp
private const val GROW_FROM_SCALE = 0.3f

/** Rows start once the panel is about a third of the way open. */
private const val ROWS_DELAY_MILLIS = 80
private const val CLOSE_FADE_START = 0.4f
private const val PANEL_FADE_IN = 0.2f
private const val SCRIM_ALPHA = 0.08f
private const val SQUASH = 0.1f
private const val MAX_SQUASH = 0.04f
private const val CANCEL_SHRINK = 0.03f
private const val MIN_SCALE = 0.001f
