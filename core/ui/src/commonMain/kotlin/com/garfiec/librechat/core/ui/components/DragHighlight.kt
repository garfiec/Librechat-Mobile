package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    }

    fun targetAt(screen: Offset): DragHighlightTargetNode? =
        targets.firstOrNull { it.screenBounds()?.contains(screen) == true }

    /** Clears the previous gesture's leftovers at once. */
    fun reset() {
        hovered = null
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

    fun hover(target: DragHighlightTargetNode?) {
        if (target === hovered) return
        hovered = target
        val bounds = target?.screenBounds()
        if (bounds == null) {
            scope.launch { alpha.animateTo(0f, tween(HIGHLIGHT_FADE_MILLIS)) }
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        left = bounds.left
        right = bounds.right
        if (alpha.targetValue == 0f) {
            scope.launch {
                top.snapTo(bounds.top)
                bottom.snapTo(bounds.bottom)
            }
        } else {
            val down = bounds.top > top.targetValue
            scope.launch { top.animateTo(bounds.top, if (down) TrailSpec else LeadSpec) }
            scope.launch { bottom.animateTo(bounds.bottom, if (down) LeadSpec else TrailSpec) }
        }
        scope.launch { alpha.animateTo(1f, tween(HIGHLIGHT_FADE_MILLIS)) }
    }
}

/** On a row's leading icon, inside a drag-select row: grows while the row is highlighted. */
fun Modifier.dragSelectRowIcon(): Modifier = this then DragSelectRowIconElement

internal fun Modifier.dragHighlightTarget(highlight: DragHighlight?, onSelect: () -> Unit): Modifier =
    if (highlight == null) this else this then DragHighlightTargetElement(highlight, onSelect)

/** The highlight, from screen coordinates into [inside] (which may be transformed). */
internal fun DrawScope.drawHighlight(highlight: DragHighlight, inside: LayoutCoordinates?, color: Color) {
    val alpha = highlight.alpha.value
    if (alpha <= 0f || inside == null || !inside.isAttached) return
    val topLeft = inside.screenToLocal(Offset(highlight.left, highlight.top.value))
    val bottomRight = inside.screenToLocal(Offset(highlight.right, highlight.bottom.value))
    if (!topLeft.isSpecified || !bottomRight.isSpecified) return
    val inset = HighlightInset.toPx()
    val height = bottomRight.y - topLeft.y
    if (height <= 0f) return
    drawRoundRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = Offset(topLeft.x + inset, topLeft.y),
        size = Size(bottomRight.x - topLeft.x - 2 * inset, height),
        cornerRadius = CornerRadius(min(HighlightRadius.toPx(), height / 2f)),
    )
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
        val overlap = min(highlight.bottom.value, bounds.bottom) - max(highlight.top.value, bounds.top)
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
private const val HIGHLIGHT_FADE_MILLIS = 100

private val LeadSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 1400f)
private val TrailSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
