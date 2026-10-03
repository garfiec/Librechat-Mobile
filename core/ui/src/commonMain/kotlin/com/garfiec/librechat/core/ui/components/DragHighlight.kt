package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The highlight a drag-to-select gesture moves between rows: it springs to the row under the
 * finger, the hovered row nudges sideways while the others dim, and the row's icon grows. Rows
 * register through [DragHighlightTargetNode] and are hit-tested in screen coordinates, so the
 * gesture can live on any layout (a menu's opener, a list's container).
 */
@Stable
internal class DragHighlight(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    private val targets = mutableListOf<DragHighlightTargetNode>()
    private var hovered: DragHighlightTargetNode? = null

    // The row last hovered and where it was then. It stays set while the highlight fades out, so
    // the highlight rides along if that row moves (a sheet dragged away, a list scrolled).
    private var anchor: DragHighlightTargetNode? = null
    private var anchorOrigin = Offset.Zero

    // Set by [reset] until the next hover: its snap to transparent is still queued, so alpha's target
    // may still be the last gesture's fade-in, and the highlight must not spring over from there.
    private var fresh = false

    // In screen coordinates. Its top and bottom ride separate springs so it stretches as it
    // travels: the leading edge is stiff, the trailing one lags.
    val top = Animatable(0f)
    val bottom = Animatable(0f)
    var left by mutableFloatStateOf(0f)
        private set
    var right by mutableFloatStateOf(0f)
        private set
    val alpha = Animatable(0f)

    fun register(target: DragHighlightTargetNode) {
        targets += target
    }

    fun unregister(target: DragHighlightTargetNode) {
        targets -= target
        if (hovered === target) hover(null)
        if (anchor === target) anchor = null
    }

    /** How far the anchor row has moved on screen since it was hovered; add it to the screen geometry. */
    fun drift(): Offset {
        val origin = anchor?.screenBounds()?.topLeft ?: return Offset.Zero
        return origin - anchorOrigin
    }

    fun targetAt(screen: Offset): DragHighlightTargetNode? =
        targets.firstOrNull { it.screenBounds()?.contains(screen) == true }

    private val exclusions = mutableListOf<DragSelectExcludeNode>()

    fun exclude(node: DragSelectExcludeNode) {
        exclusions += node
    }

    fun include(node: DragSelectExcludeNode) {
        exclusions -= node
    }

    /** Whether [screen] is on a control inside a row that keeps its press to itself. */
    fun isExcluded(screen: Offset): Boolean = exclusions.any { it.screenBounds()?.contains(screen) == true }

    /** Clears the previous gesture's leftovers at once. */
    fun reset() {
        hovered = null
        anchor = null
        fresh = true
        scope.launch { alpha.snapTo(0f) }
    }

    /** Ends a drag over a row: selects it and returns its bounds, or null off every row. */
    fun select(): Rect? {
        val target = hovered ?: return null
        val bounds = target.screenBounds() ?: return null
        hovered = null
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        target.onSelect()
        return bounds
    }

    /**
     * Ends a press that wasn't taken over by a scroll: a highlight still fading in finishes first, so
     * even a quick tap shows, then it fades out. Also clears one a selection left on.
     */
    fun release() {
        hovered = null
        scope.launch {
            if (alpha.targetValue > 0f) alpha.animateTo(1f, FadeInSpec)
            alpha.animateTo(0f, FadeOutSpec)
        }
    }

    /** Moves the highlight to [target] (null fades it); [tick] plays the row-change haptic. */
    fun hover(target: DragHighlightTargetNode?, tick: Boolean = true) {
        if (target === hovered) return
        hovered = target
        val bounds = target?.screenBounds()
        if (bounds == null) {
            scope.launch { alpha.animateTo(0f, FadeOutSpec) }
            return
        }
        if (tick) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        anchor = target
        anchorOrigin = bounds.topLeft
        left = bounds.left
        right = bounds.right
        if (fresh || alpha.targetValue == 0f) {
            fresh = false
            scope.launch {
                top.snapTo(bounds.top)
                bottom.snapTo(bounds.bottom)
            }
        } else {
            val down = bounds.top > top.targetValue
            scope.launch { top.animateTo(bounds.top, if (down) TrailSpec else LeadSpec) }
            scope.launch { bottom.animateTo(bounds.bottom, if (down) LeadSpec else TrailSpec) }
        }
        scope.launch { alpha.animateTo(1f, FadeInSpec) }
    }
}

/** On a row's leading icon, inside a drag-select row: grows while the row is highlighted. */
fun Modifier.dragSelectRowIcon(): Modifier = this then DragSelectRowIconElement

internal fun Modifier.dragSelectExclude(highlight: DragHighlight?): Modifier =
    if (highlight == null) this else this then DragSelectExcludeElement(highlight)

private data class DragSelectExcludeElement(val highlight: DragHighlight) : ModifierNodeElement<DragSelectExcludeNode>() {
    override fun create() = DragSelectExcludeNode(highlight)

    override fun update(node: DragSelectExcludeNode) {
        if (node.highlight === highlight) return
        node.highlight.include(node)
        node.highlight = highlight
        if (node.isAttached) highlight.exclude(node)
    }
}

internal class DragSelectExcludeNode(var highlight: DragHighlight) : Modifier.Node(), GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null

    override fun onAttach() = highlight.exclude(this)

    override fun onDetach() {
        highlight.include(this)
        coordinates = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    fun screenBounds(): Rect? = coordinates?.screenBounds()
}

internal fun Modifier.dragHighlightTarget(highlight: DragHighlight?, onSelect: () -> Unit): Modifier =
    if (highlight == null) this else this then DragHighlightTargetElement(highlight, onSelect)

/** The highlight, from screen coordinates into [inside] (which may be transformed). */
internal fun DrawScope.drawHighlight(highlight: DragHighlight, inside: LayoutCoordinates?, color: Color) {
    val alpha = highlight.alpha.value
    if (alpha <= 0f || inside == null || !inside.isAttached) return
    val drift = highlight.drift()
    val topLeft = inside.screenToLocal(Offset(highlight.left, highlight.top.value) + drift)
    val bottomRight = inside.screenToLocal(Offset(highlight.right, highlight.bottom.value) + drift)
    if (!topLeft.isSpecified || !bottomRight.isSpecified) return
    val size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
    if (size.height <= 0f) return
    translate(topLeft.x, topLeft.y) {
        drawOutline(
            DragSelectRowShape.createOutline(size, layoutDirection, this),
            color = color.copy(alpha = color.alpha * alpha),
        )
    }
}

/**
 * The drag highlight's shape over a row: inset from the row's sides and rounded. Rows clip their
 * indication to it ([dragSelectRow]), so focus and hover fill the same shape the highlight does.
 */
val DragSelectRowShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val inset = with(density) { HighlightInset.toPx() }
        val radius = min(with(density) { HighlightRadius.toPx() }, size.height / 2f)
        return Outline.Rounded(
            RoundRect(
                left = inset,
                top = 0f,
                right = max(inset, size.width - inset),
                bottom = size.height,
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
}

internal fun LayoutCoordinates.screenBounds(): Rect? {
    if (!isAttached) return null
    val topLeft = localToScreen(Offset.Zero)
    if (!topLeft.isSpecified) return null
    return Rect(topLeft, localToScreen(Offset(size.width.toFloat(), size.height.toFloat())))
}

private val ModifierLocalRowLift = modifierLocalOf<() -> Float> { { 0f } }

private data class DragHighlightTargetElement(
    val highlight: DragHighlight,
    val onSelect: () -> Unit,
) : ModifierNodeElement<DragHighlightTargetNode>() {
    override fun create() = DragHighlightTargetNode(highlight, onSelect)

    override fun update(node: DragHighlightTargetNode) {
        if (node.highlight !== highlight) {
            node.highlight.unregister(node)
            node.highlight = highlight
            if (node.isAttached) highlight.register(node)
        }
        node.onSelect = onSelect
    }
}

/**
 * A row's lift is how much of the highlight covers it, times the highlight's opacity — so it rides
 * the highlight's springs rather than animating on its own.
 */
internal class DragHighlightTargetNode(
    var highlight: DragHighlight,
    var onSelect: () -> Unit,
) : Modifier.Node(), LayoutModifierNode, GlobalPositionAwareModifierNode, ModifierLocalModifierNode {
    private var coordinates: LayoutCoordinates? = null

    override val providedValues = modifierLocalMapOf(ModifierLocalRowLift to { lift() })

    override fun onAttach() = highlight.register(this)

    override fun onDetach() {
        highlight.unregister(this)
        coordinates = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    fun screenBounds(): Rect? = coordinates?.screenBounds()

    fun lift(): Float {
        val alpha = highlight.alpha.value
        if (alpha == 0f) return 0f
        val bounds = screenBounds() ?: return 0f
        if (bounds.height <= 0f) return 0f
        val dy = highlight.drift().y
        val overlap = min(highlight.bottom.value + dy, bounds.bottom) - max(highlight.top.value + dy, bounds.top)
        return alpha * (overlap / bounds.height).coerceIn(0f, 1f)
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
        val nudge = RowNudge.toPx() * direction
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val lift = lift()
                translationX = lift * nudge
                alpha = 1f - ROW_DIM * highlight.alpha.value * (1f - lift)
            }
        }
    }
}

private object DragSelectRowIconElement : ModifierNodeElement<DragSelectRowIconNode>() {
    override fun create() = DragSelectRowIconNode()
    override fun update(node: DragSelectRowIconNode) = Unit
    override fun hashCode() = 0
    override fun equals(other: Any?) = other === this
}

private class DragSelectRowIconNode : Modifier.Node(), LayoutModifierNode, ModifierLocalModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val scale = 1f + ICON_GROWTH * ModifierLocalRowLift.current()
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** M3's pressed state-layer opacity, so the drag highlight reads as a held press. */
internal const val DRAG_HIGHLIGHT_ALPHA = 0.10f

private val HighlightInset = 4.dp
private val HighlightRadius = 12.dp
private val RowNudge = 4.dp
private const val ROW_DIM = 0.15f
private const val ICON_GROWTH = 0.15f
private val FadeInSpec = tween<Float>(durationMillis = 150, easing = LinearOutSlowInEasing)
private val FadeOutSpec = tween<Float>(durationMillis = 250, easing = FastOutLinearInEasing)

private val LeadSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 1400f)
private val TrailSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
