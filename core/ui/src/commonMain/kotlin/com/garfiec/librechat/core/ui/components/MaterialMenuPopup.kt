package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Material dropdown menu: placed exactly where M3's `DropdownMenu` places it, but it unfolds out
 * of its anchor — a clip that starts as an anchor-sized pill at the menu's corner nearest the anchor
 * and springs out to the full panel — and its rows arrive in a short top-to-bottom stagger.
 */
@Composable
internal fun MaterialMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    scrollState: ScrollState,
    properties: PopupProperties,
    shape: Shape,
    containerColor: Color,
    dragSelection: MenuDragSelection?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return

    val density = LocalDensity.current
    // The anchor's bounds in the menu's own coordinates; set by the positioner before first draw.
    var anchorInMenu by remember { mutableStateOf<IntRect?>(null) }
    val positionProvider = remember(offset, density) {
        MaterialMenuPositionProvider(offset, density) { anchor, menu -> anchorInMenu = anchor.translate(-menu.topLeft) }
    }

    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
        properties = properties.withPredictiveBack(),
    ) {
        val transition = rememberTransition(expandedState, label = "MaterialMenu")
        val unfold = transition.animateFloat(
            transitionSpec = { if (targetState) UnfoldSpec else FoldSpec },
            label = "unfold",
        ) { if (it) 1f else 0f }
        val rowsClock = transition.animateFloat(
            transitionSpec = { if (targetState) tween(RowsTotalMillis, easing = LinearEasing) else snap() },
            label = "rows",
        ) { if (it) 1f else 0f }
        // Rows stagger in on open only; while closing they stay put and fold away with the panel.
        val rowsProgress = remember {
            object : State<Float> {
                override val value: Float get() = if (expandedState.targetState) rowsClock.value else 1f
            }
        }
        val elevation = MenuDefaults.ShadowElevation
        // A back gesture folds the menu part way toward its anchor as it moves; completing it closes
        // the menu from there, abandoning it springs the menu back open.
        val backFold = remember { Animatable(0f) }
        val backScope = rememberCoroutineScope()
        LaunchedEffect(expandedState.targetState) { if (expandedState.targetState) backFold.snapTo(0f) }
        MenuPredictiveBackEffect(
            enabled = expandedState.targetState && properties.dismissOnBackPress,
            dismissOnOutsideTap = properties.focusable && properties.dismissOnClickOutside,
            onOutsideTap = { currentOnDismiss() },
            onProgress = { progress -> backScope.launch { backFold.snapTo(progress) } },
            onCancel = { backScope.launch { backFold.animateTo(0f, BackCancelSpec) } },
            onCommit = { currentOnDismiss() },
        )
        val drag = dragSelection
        val opensDown = { anchorInMenu.let { it == null || it.top < 0 } }
        val effects = rememberMenuDragEffects(drag, opening = expandedState.targetState, opensDown = opensDown)
        val stretch = effects.stretch
        val lean = effects.lean
        val shrink = effects.shrink
        val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DRAG_HIGHLIGHT_ALPHA)
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.contentColorFor(containerColor)) {
            Layout(
                content = { MenuColumnScope.content() },
                modifier = Modifier
                    .onPlaced { effects.frame = it }
                    .graphicsLayer {
                        val p = unfold.value * (1f - BACK_FOLD * backFold.value)
                        val revealed = p.coerceIn(0f, 1f)
                        val full = Rect(Offset0, size)
                        val chosen = drag?.selected
                        val start = if (chosen != null && !expandedState.targetState) {
                            effects.frame?.toLocal(chosen) ?: unfoldStart(anchorInMenu, size)
                        } else {
                            unfoldStart(anchorInMenu, size)
                        }
                        val endRadius = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f
                        this.shape = if (revealed >= 1f) {
                            shape
                        } else {
                            val rect = lerp(start, full, revealed)
                            val radius = lerp(start.minDimension / 2f, endRadius, revealed)
                            RoundRectShape(rect, radius.coerceAtMost(rect.minDimension / 2f))
                        }
                        clip = true
                        shadowElevation = elevation.toPx()
                        alpha = (p / FADE_IN_FRACTION).coerceIn(0f, 1f) * (1f - BACK_FADE * backFold.value)
                        val squeeze = 1f - CANCEL_SHRINK * shrink.value
                        scaleX = squeeze
                        scaleY = squeeze * (1f + stretch.value / size.height.coerceAtLeast(1f))
                        transformOrigin = TransformOrigin(0.5f, if (opensDown()) 0f else 1f)
                        translationX = lean.value.x
                        translationY = lean.value.y
                    }
                    .background(containerColor)
                    .onPlaced { effects.inside = it }
                    .drawBehind { if (drag != null) drawHighlight(drag.highlight, effects.inside, highlightColor) }
                    // While closing, rows are on their way out: swallow taps so one can't fire twice.
                    .then(if (expandedState.targetState) Modifier else Modifier.pointerInput(Unit) { consumeAll() })
                    .then(modifier)
                    .padding(vertical = MenuVerticalPadding)
                    .width(IntrinsicSize.Max)
                    .verticalScroll(scrollState),
                measurePolicy = remember(density) {
                    StaggeredColumnPolicy(
                        rowProgress = { index -> rowProgress(rowsProgress.value, index) },
                        rowShiftPx = with(density) { RowShift.toPx() },
                        opensDown = opensDown,
                    )
                },
            )
        }
    }
}

/**
 * A menu panel's response to a press-drag-release ([MenuDragSelection]): it stretches past its far
 * edge toward the finger, leans toward it, and shrinks a little while a release would cancel.
 * [frame] is the panel's untransformed layout (what these are measured against), [inside] its
 * transformed inside (where the highlight is drawn).
 */
@Stable
internal class MenuDragEffects {
    var frame by mutableStateOf<LayoutCoordinates?>(null)
    var inside: LayoutCoordinates? = null
    val stretch = Animatable(0f)
    val lean = Animatable(Offset.Zero, Offset.VectorConverter)
    val shrink = Animatable(0f)
}

@Composable
internal fun rememberMenuDragEffects(drag: MenuDragSelection?, opening: Boolean, opensDown: () -> Boolean): MenuDragEffects {
    val effects = remember { MenuDragEffects() }
    if (drag == null) return effects
    val density = LocalDensity.current
    val currentOpensDown by rememberUpdatedState(opensDown)
    LaunchedEffect(drag, opening) {
        if (opening) drag.reset()
        drag.menuOpenChanged(opening)
    }
    DisposableEffect(drag) { onDispose { drag.menuOpenChanged(false) } }
    val frame = effects.frame
    DisposableEffect(drag, frame) {
        drag.panel = frame
        onDispose { if (drag.panel === frame) drag.panel = null }
    }
    LaunchedEffect(drag) {
        snapshotFlow { drag.finger to drag.cancelArmed }.collectLatest { (finger, armed) ->
            val coords = effects.frame?.takeIf { it.isAttached }
            var stretchTo = 0f
            var leanTo = Offset.Zero
            if (finger != null && coords != null && !armed) {
                val local = coords.screenToLocal(finger)
                if (local.isSpecified) {
                    val height = coords.size.height.toFloat()
                    val past = if (currentOpensDown()) local.y - height else -local.y
                    stretchTo = rubberBand(past, with(density) { StretchCap.toPx() }, STRETCH_SOFTNESS)
                    val center = Offset(coords.size.width / 2f, height / 2f)
                    leanTo = rubberVector(local - center, with(density) { PanelLeanCap.toPx() }, PANEL_LEAN_SOFTNESS)
                }
            }
            coroutineScope {
                launch { effects.stretch.animateTo(stretchTo, PanelTrackSpec) }
                launch { effects.lean.animateTo(leanTo, PanelOffsetSpec) }
                launch { effects.shrink.animateTo(if (armed) 1f else 0f, PanelTrackSpec) }
            }
        }
    }
    return effects
}

internal fun LayoutCoordinates.toLocal(screen: Rect): Rect? {
    if (!isAttached) return null
    val topLeft = screenToLocal(screen.topLeft)
    val bottomRight = screenToLocal(screen.bottomRight)
    if (!topLeft.isSpecified || !bottomRight.isSpecified) return null
    return Rect(topLeft, bottomRight)
}

/** Where the unfold starts: the anchor's size (no bigger than the menu), at the menu's edge nearest it. */
private fun unfoldStart(anchor: IntRect?, menu: Size): Rect {
    if (anchor == null) return Rect(Offset0, Size.Zero)
    val w = min(anchor.width.toFloat(), menu.width)
    val h = min(anchor.height.toFloat(), menu.height)
    val left = anchor.left.toFloat().coerceIn(0f, menu.width - w)
    val top = anchor.top.toFloat().coerceIn(0f, menu.height - h)
    return Rect(left, top, left + w, top + h)
}

private class RoundRectShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

/**
 * A `Column` whose children each sit on their own layer, so the open stagger only re-draws them.
 * Measures like the `Column` M3 uses: children at min width 0, the column as wide as its widest.
 */
internal class StaggeredColumnPolicy(
    private val rowProgress: (index: Int) -> Float,
    private val rowShiftPx: Float,
    private val opensDown: () -> Boolean,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                placeable.placeWithLayer(0, y) {
                    val t = rowProgress(index)
                    alpha = t
                    translationY = (1f - t) * rowShiftPx * if (opensDown()) -1f else 1f
                    val scale = ROW_START_SCALE + (1f - ROW_START_SCALE) * t
                    scaleX = scale
                    scaleY = scale
                }
                y += placeable.height
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        measurables.sumOf { it.minIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        measurables.sumOf { it.maxIntrinsicHeight(width) }
}

/** Row [index]'s eased 0..1 at [clock] (0..1 over [RowsTotalMillis]); rows past the cap start together. */
internal fun rowProgress(clock: Float, index: Int): Float {
    val elapsed = clock * RowsTotalMillis - min(index, MAX_STAGGERED_ROWS) * ROW_STAGGER_MILLIS
    return FastOutSlowInEasing.transform((elapsed / ROW_MILLIS).coerceIn(0f, 1f))
}

/**
 * The menu's content receiver. `weight`/`align` are no-ops: menu content is a stack of full-width
 * items and dividers, and no caller uses them. Wire them through before a menu needs them.
 */
internal object MenuColumnScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/**
 * M3's `DropdownMenuPositionProvider` (internal there), rule for rule, so a Material menu opens
 * exactly where it always has. Horizontally: start to the anchor's start, else end to its end, else
 * the nearer window edge. Vertically: below the anchor, else above, else centred on its top, else the
 * nearer window edge, keeping [MenuVerticalMargin] from the window's top and bottom.
 */
internal class MaterialMenuPositionProvider(
    private val offset: DpOffset,
    private val density: Density,
    private val onPositioned: (anchor: IntRect, menu: IntRect) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val ltr = layoutDirection == LayoutDirection.Ltr
        val margin = with(density) { MenuVerticalMargin.roundToPx() }
        val dx = with(density) { offset.x.roundToPx() }.let { if (ltr) it else -it }
        val dy = with(density) { offset.y.roundToPx() }
        val w = popupContentSize.width
        val h = popupContentSize.height

        val windowEdgeX = when {
            w >= windowSize.width -> ((windowSize.width - w) / 2f).roundToInt()
            anchorBounds.center.x < windowSize.width / 2 -> 0
            else -> windowSize.width - w
        }
        val x = listOf(
            (if (ltr) anchorBounds.left else anchorBounds.right - w) + dx,
            (if (ltr) anchorBounds.right - w else anchorBounds.left) + dx,
        ).firstOrNull { it >= 0 && it + w <= windowSize.width } ?: windowEdgeX

        val windowEdgeY = when {
            h >= windowSize.height - 2 * margin -> ((windowSize.height - h) / 2f).roundToInt()
            anchorBounds.center.y < windowSize.height / 2 -> margin
            else -> windowSize.height - margin - h
        }
        val y = listOf(
            anchorBounds.bottom + dy,
            anchorBounds.top - h + dy,
            anchorBounds.top - (h / 2f).roundToInt() + dy,
        ).firstOrNull { it >= margin && it + h <= windowSize.height - margin } ?: windowEdgeY

        val position = IntOffset(x, y)
        onPositioned(anchorBounds, IntRect(position, popupContentSize))
        return position
    }
}

private val Offset0 = Offset.Zero
private val MenuVerticalMargin = 48.dp
internal val MenuVerticalPadding = 8.dp
internal val RowShift = 8.dp
private const val ROW_START_SCALE = 0.92f
private val StretchCap = 12.dp
private val PanelLeanCap = 4.dp
private const val STRETCH_SOFTNESS = 0.3f
private const val PANEL_LEAN_SOFTNESS = 0.05f
private const val CANCEL_SHRINK = 0.03f

/** How far a full back gesture folds the menu toward its anchor before release. */
internal const val BACK_FOLD = 0.4f

/**
 * How much of the panel's opacity a full back gesture takes away. It stays applied through the
 * close that follows, so a committed gesture fades on from where it left off.
 */
internal const val BACK_FADE = 0.5f
internal val BackCancelSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)

private val PanelTrackSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 1500f)
private val PanelOffsetSpec = spring<Offset>(dampingRatio = 0.9f, stiffness = 1500f)

/** Slightly underdamped: the panel lands with a little weight rather than stopping dead. */
private val UnfoldSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 260f)
private val FoldSpec = tween<Float>(durationMillis = 250, easing = FastOutLinearInEasing)

/** The panel is fully opaque by this fraction of the unfold, so the pill reads as solid from the start. */
private const val FADE_IN_FRACTION = 0.3f
private const val ROW_MILLIS = 240
private const val ROW_STAGGER_MILLIS = 30
private const val MAX_STAGGERED_ROWS = 6
internal const val RowsTotalMillis = ROW_MILLIS + MAX_STAGGERED_ROWS * ROW_STAGGER_MILLIS
